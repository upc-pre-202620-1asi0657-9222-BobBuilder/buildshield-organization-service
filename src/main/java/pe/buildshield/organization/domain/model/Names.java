package pe.buildshield.organization.domain.model;

import pe.buildshield.organization.shared.error.ErrorDetail;
import pe.buildshield.organization.shared.error.ValidationException;

import java.util.List;

/** Validación común de nombres visibles (obra, almacén, material). */
final class Names {

    static final int MAX_NAME = 120;

    private Names() {
    }

    static String require(String name, String field) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME) {
            throw new ValidationException("INVALID_NAME", "El nombre es obligatorio (máximo 120 caracteres)",
                    List.of(new ErrorDetail(field, "obligatorio, máximo 120 caracteres")));
        }
        return name.trim();
    }
}
