package com.exemple.historique.controller;

import com.exemple.historique.client.CalculClient;
import com.exemple.historique.model.Operation;
import com.exemple.historique.service.OperationService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/operations")
public class OperationController {
    private static final Set<String> OPERATEURS =
            Set.of("somme", "soustraction", "produit", "division");

    private final CalculClient calculClient;
    private final OperationService operationService;

    public OperationController(CalculClient calculClient, OperationService operationService) {
        this.calculClient = calculClient;
        this.operationService = operationService;
    }

    private ResponseEntity<Object> calculer(Operation operation) {
        if (operation == null || !OPERATEURS.contains(operation.getOperateur())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Operateur inconnu : "
                            + (operation == null ? "null" : operation.getOperateur())));
        }
        try {
            String corps = calculClient.calculer(operation.getOperateur(),
                    operation.getA(), operation.getB());
            operation.setResultat(Double.parseDouble(corps.trim()));
            return null;
        } catch (FeignException.BadRequest exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", exception.contentUTF8()));
        } catch (FeignException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("erreur", "calcul-service injoignable"));
        }
    }

    @PostMapping
    public ResponseEntity<?> creer(@RequestBody Operation operation) {
        ResponseEntity<Object> erreur = calculer(operation);
        if (erreur != null) {
            return erreur;
        }
        Operation sauvegardee = operationService.ajouter(operation);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(sauvegardee.getId()).toUri();
        return ResponseEntity.created(location).body(sauvegardee);
    }

    @GetMapping
    public ResponseEntity<List<Operation>> lister() {
        return ResponseEntity.ok(operationService.lister());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Operation> lire(@PathVariable int id) {
        Operation operation = operationService.lire(id);
        return operation == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(operation);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> modifier(@PathVariable int id, @RequestBody Operation operation) {
        if (!operationService.existe(id)) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<Object> erreur = calculer(operation);
        if (erreur != null) {
            return erreur;
        }
        return ResponseEntity.ok(operationService.remplacer(id, operation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable int id) {
        if (!operationService.existe(id)) {
            return ResponseEntity.notFound().build();
        }
        operationService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
