package com.lendbridge.notification.template;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TemplateRenderer {
  public String render(String template, Map<String, String> values) {
    String result = template;
    for (var entry : values.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
    }
    return result;
  }
}
