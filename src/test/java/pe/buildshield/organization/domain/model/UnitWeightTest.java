package pe.buildshield.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.organization.shared.error.ValidationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitWeightTest {

    @Test
    void keeps_three_decimals() {
        assertThat(new UnitWeight(new BigDecimal("42.5")).kg()).isEqualTo(new BigDecimal("42.500"));
        assertThat(UnitWeight.ZERO.kg()).isEqualTo(new BigDecimal("0.000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.001", "0.0001", "10000000"})
    void rejects_negative_weights_more_than_three_decimals_and_overflow(String value) {
        assertThatThrownBy(() -> new UnitWeight(new BigDecimal(value))).isInstanceOf(ValidationException.class)
                .hasMessageContaining("peso unitario");
    }

    @Test
    void weight_is_required() {
        assertThatThrownBy(() -> new UnitWeight(null)).isInstanceOf(ValidationException.class);
    }
}
