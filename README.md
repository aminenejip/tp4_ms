# TP4 — Spring Cloud : Microservices « Calculatrice »

## Partie A — Questions de conception (A.4)

**1. Comment un service retrouve-t-il les adresses des instances à partir de leur nom ?**

C'est **Eureka Server** qui fait le lien entre le nom d'un service, par exemple `CALCUL-SERVICE`, et ses instances disponibles. Le registre évolue lorsqu'une instance démarre et s'inscrit, se désinscrit ou n'envoie plus ses signaux de présence (*heartbeats*). Les clients récupèrent ensuite ces informations pour trouver une instance utilisable.

**2. Quel est l'avantage et l'inconvénient d'une passerelle unique ?**

L'avantage principal est que le client utilise **une seule adresse** : il n'a pas à connaître les ports de chaque microservice. En revanche, la passerelle devient un point sensible de l'architecture : si elle tombe en panne et qu'aucune autre instance ne prend le relais, les clients ne peuvent plus accéder aux services.

**3. Comment six requêtes sont-elles réparties entre les instances 8081 et 8083 ?**

Avec la stratégie *round-robin* et deux instances disponibles, on s'attend à **trois requêtes par instance**. Le client reçoit normalement la même réponse dans les deux cas (`2`) et ne voit donc pas quelle instance a effectué le calcul. On peut le vérifier avec les traces affichées dans les consoles.

## Partie B — Eureka Server

**Question B.1 — Pourquoi les applications peuvent-elles fonctionner sur le même poste sans conflit ?**

Chaque microservice tourne dans sa **propre JVM** et possède son serveur embarqué. Il suffit qu'ils écoutent sur des **ports différents** : Eureka sur `8761`, calcul-service sur `8081` et historique-service sur `8082`. Ils peuvent ainsi fonctionner en parallèle sur le même ordinateur.

## Partie C — Enregistrement des microservices

**Question C.1 — À quoi servent le nom du service et l'identifiant de chaque instance ?**

Le nom `CALCUL-SERVICE` sert aux autres microservices pour **chercher le service**, sans connaître son adresse. Les identifiants `calcul-service:8081` et `calcul-service:8083` permettent, eux, de **distinguer les deux instances** enregistrées dans Eureka. Le répartiteur peut alors sélectionner l'une ou l'autre.

**Question C.2 — Que se passe-t-il si on lance la deuxième instance sans `--server.port=8083` ?**

Elle essaie d'utiliser le port `8081`, déjà occupé par la première instance. Son démarrage échoue donc avec une erreur indiquant que le port est utilisé. Il faut attribuer un port différent à chaque instance lancée sur la même machine.

## Partie D — OpenFeign et LoadBalancer

**Question D.1 — Quelle règle est utilisée pour choisir les instances ?**

Spring Cloud LoadBalancer utilise ici une répartition **round-robin** : il choisit les instances à tour de rôle. Avec deux instances disponibles, six appels devraient donc se répartir approximativement, et normalement en alternance, entre `8081` et `8083`. L'ordre exact se vérifie dans les consoles.

**Question D.2 — Que se passe-t-il si le nom devient `CALCUL-SERVIC` ?**

Ce nom ne correspond à aucun service enregistré dans Eureka. OpenFeign ne peut donc pas trouver d'instance à contacter. Dans le contrôleur du TP, l'erreur est prise en charge par le bloc `catch (FeignException e)`, qui renvoie **HTTP 503 — Service Unavailable**. Il faut ensuite rétablir `CALCUL-SERVICE`.

## Partie E — Spring Cloud Gateway

**Question E.1 — Pourquoi l'en-tête `Location` contient-il le port 8888 au lieu de 8082 ?**

La requête arrive d'abord à Gateway sur le port `8888`, puis elle est transmise à historique-service. La passerelle fournit les en-têtes `X-Forwarded-*`, qui renseignent notamment l'adresse vue par le client. Grâce à `server.forward-headers-strategy=framework`, historique-service peut reconstruire cette adresse publique pour l'en-tête `Location`, au lieu d'utiliser son port interne `8082`.

**Question E.2 — Quelle différence entre les URI des étapes E.3 et E.5 ?**

En **E.3**, les routes sont définies manuellement et gardent des URI courtes, comme `/calcul/somme`. C'est simple pour le client, mais il faut ajouter une route dans Gateway à chaque nouveau service. En **E.5**, les routes sont créées automatiquement depuis Eureka et incluent le nom du service, par exemple `/calcul-service/calcul/somme`. Cette deuxième solution facilite l'ajout de nouveaux microservices sans modifier les routes de Gateway.

**Question E.3 — Pourquoi Gateway possède-t-elle une route vers elle-même ? Est-ce gênant ?**

Gateway est elle-même enregistrée dans Eureka. Le mécanisme de découverte automatique lui crée donc une route, comme pour les autres services. Ce n'est pas bloquant pour le TP, mais cette route n'est pas forcément utile et doit être contrôlée en pratique, notamment pour éviter d'exposer inutilement des points d'administration ou de provoquer un routage en boucle.

## Questions de synthèse

**1. À quelles étapes observe-t-on les différences entre le TP3 et le TP4 ?**

| Différence du tableau A.3 | Étape du TP4 |
|---|---|
| Nombre de programmes et organisation en modules | B.0, B.1, C.0 et E.1 |
| Nouvelles dépendances Maven | B.2, C.1, C.7 et E.1 |
| Découverte des services au lieu d'une adresse fixe | C.2, C.7 et D.2 |
| Appel entre services avec OpenFeign | D.1 à D.4 |
| Gestion des erreurs d'appel | D.3 et D.4 |
| Point d'entrée unique | E.2 |
| Plusieurs instances et répartition de charge | C.5, D.4 et E.3 |
| Tomcat pour les services, Netty pour Gateway | B.5, C.4 et E.2 |
| Comportement lors de l'arrêt d'une instance | C.6 et E.3 |
| Tests avec Swagger UI | C.4, D.4, E.2 et E.5 |
| Génération des méthodes avec Lombok | C.1 bis et C.7 |

**2. Quel est le trajet complet de `POST /historique-service/operations` ?**

Le client envoie la requête à **Gateway (`8888`)**. Gateway utilise les informations d'Eureka pour trouver `HISTORIQUE-SERVICE` et transmet la requête à l'instance `8082`, après avoir retiré le préfixe `/historique-service`. Historique-service appelle ensuite `CALCUL-SERVICE` avec OpenFeign, via `GET /calcul/somme?a=1&b=2`. Son LoadBalancer choisit une instance disponible (`8081` ou `8083`) à partir des informations de découverte récupérées auprès d'Eureka. Le service de calcul renvoie `3`, historique-service enregistre l'opération, puis la réponse **201 Created** remonte par Gateway jusqu'au client.

**3. Quels sont les trois programmes qui disposent de Spring Cloud LoadBalancer ?**

Dans ce TP, **calcul-service**, **historique-service** et **gateway-service** disposent du client Eureka, qui apporte aussi le support LoadBalancer. En revanche, ce sont surtout **historique-service** et **gateway-service** qui l'utilisent réellement pour leurs appels sortants : historique-service choisit une instance de `CALCUL-SERVICE`, tandis que Gateway résout les services désignés par ses routes `lb://`, notamment `CALCUL-SERVICE` et `HISTORIQUE-SERVICE`. Avec les routes dynamiques, elle peut aussi résoudre `GATEWAY-SERVICE`. Calcul-service n'appelle aucun autre microservice dans cette application : il n'a donc pas de nom distant à résoudre.

**4. Quelles dépendances sont nécessaires dans chaque service ?**

| Dépendance | calcul-service | historique-service | gateway-service |
|---|---|---|---|
| Eureka Client | Oui, pour s'enregistrer | Oui, pour s'enregistrer et découvrir calcul-service | Oui, pour découvrir les destinations et s'enregistrer |
| OpenFeign | Non | Oui, pour appeler calcul-service | Non |
| Spring Cloud Gateway | Non | Non | Oui, pour le routage |

**5. Comment le TP4 résout-il le problème d'une adresse écrite en dur ?**

Avec une adresse fixe comme `http://localhost:8081`, historique-service ne contacte qu'une seule instance, même si plusieurs sont disponibles. Dans le TP4, **Eureka** connaît les instances actives, **OpenFeign** permet d'appeler un service par son nom et **LoadBalancer** répartit les appels. **Gateway** applique la même idée pour les requêtes des clients. On peut donc changer ou ajouter des instances sans modifier leurs adresses dans le code des autres services.

**6. Que se passe-t-il avec deux instances d'historique-service qui stockent les données en mémoire ?**

Les trois `POST` peuvent arriver sur des instances différentes (`8082` et `8084`). Comme chaque instance conserve sa propre liste, un `GET` peut ne retourner qu'une partie des opérations, selon l'instance choisie. Le problème vient du **stockage d'un état local non partagé**, qui rend les réponses incohérentes entre instances. La solution serait d'utiliser une **base de données commune**, afin que toutes les instances lisent et écrivent les mêmes opérations.

**7. Comment construire et lancer l'application sans IntelliJ ?**

Depuis le dossier racine du projet, on construit les quatre modules avec :

```bash
mvn clean package
```

Puis on lance, dans cinq terminaux différents, les applications dans l'ordre suivant (en remplaçant `*.jar` par le nom exact de chaque JAR exécutable) :

```bash
java -jar eureka-service/target/eureka-service-0.0.1-SNAPSHOT.jar
java -jar calcul-service/target/calcul-service-0.0.1-SNAPSHOT.jar --server.port=8081
java -jar calcul-service/target/calcul-service-0.0.1-SNAPSHOT.jar --server.port=8083
java -jar historique-service/target/historique-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

Eureka doit démarrer en premier pour que les services puissent s'enregistrer. Gateway est lancée en dernier.

**8. Quel est le rôle de `@GetMapping` dans le contrôleur et dans OpenFeign ?**

Dans `CalculController`, `@GetMapping` indique **quelle requête HTTP le service reçoit** et quelle méthode doit la traiter. Dans l'interface `CalculClient`, la même annotation décrit au contraire **la requête HTTP qu'OpenFeign doit envoyer** au service distant. La syntaxe est semblable, mais le sens de la communication est différent.

**9. Le regroupement dans un projet Maven remet-il en cause l'indépendance des services ?**

Non. Le projet parent facilite l'organisation du code et la compilation, mais chaque module produit son **propre JAR** et s'exécute dans **sa propre JVM**, sur **son propre port**. Il est donc possible de démarrer, arrêter ou redéployer un service sans devoir redémarrer systématiquement les autres.

**10. Pourquoi Swagger UI affiche-t-il des adresses `Servers` différentes en E.2 et E.5 ?**

En E.2, l'adresse publique est `http://localhost:8888`, car les routes statiques ne rajoutent pas de préfixe de service. En E.5, la route dynamique ajoute un préfixe, par exemple `/calcul-service`, qui apparaît aussi dans l'adresse affichée par Swagger. Les en-têtes `X-Forwarded-*` transmis par Gateway permettent de reconstruire cette adresse. Sans `server.forward-headers-strategy=framework`, Swagger risquerait d'utiliser l'adresse interne du microservice, et les requêtes envoyées avec **Execute** ne passeraient plus correctement par Gateway.

**11. Où se trouvent les méthodes générées par Lombok et pourquoi peut-on l'exclure du JAR ?**

Les getters, setters et constructeurs créés par Lombok se retrouvent dans le fichier **`Operation.class` compilé**, sous `target/classes`, et non sous forme de méthodes écrites dans `Operation.java`. Lombok intervient pendant la compilation : une fois les méthodes intégrées aux fichiers `.class`, la bibliothèque n'est plus nécessaire pour exécuter l'application. C'est pourquoi elle peut être exclue du JAR final.
