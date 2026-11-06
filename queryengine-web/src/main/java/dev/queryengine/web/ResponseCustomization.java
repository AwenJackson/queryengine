package dev.queryengine.web;

import org.springframework.beans.factory.ObjectProvider;

/**
 * 把容器里的 {@link ResponseCustomizer} 接到静态信封上。没有这个 Bean 时信封保持原样。
 */
public class ResponseCustomization {

    public ResponseCustomization(ObjectProvider<ResponseCustomizer> customizers) {
        ResponseCustomizer chain = body -> body;
        for (ResponseCustomizer customizer : customizers.orderedStream().toList()) {
            ResponseCustomizer previous = chain;
            chain = body -> customizer.customize(previous.customize(body));
        }
        ResponseEnvelope.bind(chain);
    }
}
