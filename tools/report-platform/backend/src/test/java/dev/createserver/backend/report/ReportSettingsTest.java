package dev.createserver.backend.report;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReportSettingsTest {
 @Test void strictNumericAdmins(){assertThat(ReportSettings.parse("111, 222")).containsExactly(111L,222L);assertThat(ReportSettings.parse("")).isEmpty();for(String ids:new String[]{"0","-1","username","111,","9223372036854775808"})assertThatThrownBy(()->ReportSettings.parse(ids)).isInstanceOf(IllegalArgumentException.class);}
}
