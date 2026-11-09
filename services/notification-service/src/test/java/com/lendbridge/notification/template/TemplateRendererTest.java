package com.lendbridge.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

  private final TemplateRenderer renderer = new TemplateRenderer();

  @Test
  void render_substitutesEachPlaceholder() {
    String result = renderer.render("Hello {firstName}, welcome to {product}", Map.of("firstName", "Ada", "product", "LendBridge"));

    assertThat(result).isEqualTo("Hello Ada, welcome to LendBridge");
  }

  @Test
  void render_nullValue_substitutesEmptyString() {
    Map<String, String> values = new HashMap<>();
    values.put("name", null);

    String result = renderer.render("Hi {name}!", values);

    assertThat(result).isEqualTo("Hi !");
  }

  @Test
  void render_noPlaceholders_returnsTemplateUnchanged() {
    String result = renderer.render("Plain text", Map.of());

    assertThat(result).isEqualTo("Plain text");
  }
}
