package pe.buildshield.organization.domain.model;

import pe.buildshield.organization.shared.error.ErrorDetail;
import pe.buildshield.organization.shared.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Peso de una unidad del material en kilogramos: cero o más, hasta 3 decimales y 7 enteros (numeric(10,3)). Lo usa el
 * pesaje de ordering para comparar lo despachado con lo recibido.
 */
public record UnitWeight(BigDecimal kg) {

    public static final String INVALID_UNIT_WEIGHT = "INVALID_UNIT_WEIGHT";
    private static final BigDecimal MAX = new BigDecimal("9999999.999");
    public static final UnitWeight ZERO = new UnitWeight(BigDecimal.ZERO);

    public UnitWeight {
        if (kg == null || kg.signum() < 0 || kg.compareTo(MAX) > 0 || kg.stripTrailingZeros().scale() > 3) {
            throw new ValidationException(INVALID_UNIT_WEIGHT,
                    "El peso unitario debe ser de 0 a 9999999.999 kg, con hasta 3 decimales",
                    List.of(new ErrorDetail("unitWeightKg", "entre 0 y 9999999.999, máximo 3 decimales")));
        }
        kg = kg.setScale(3, RoundingMode.UNNECESSARY);
    }
}
