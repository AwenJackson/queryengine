package dev.queryengine.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 在 Servlet 应用里注册查询路由。没有登录过滤器。
 * {@code queryengine.http.enabled=false} 时不注册。
 */
@AutoConfiguration
@AutoConfigureAfter(name = "dev.queryengine.spring.QueryEngineAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "queryengine.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        QueryHttpController.class,
        AccessHttpController.class,
        RestHttpController.class,
        GraphqlHttpController.class,
        QueryEngineWebExceptionHandler.class,
        ResponseCustomization.class
})
public class QueryEngineWebAutoConfiguration {
}
