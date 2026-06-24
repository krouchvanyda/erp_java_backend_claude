package com.company.erp.core.web;

import com.company.erp.core.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public UploadsWebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        serve(registry, props.uploads().avatar().dir(),
                props.uploads().avatar().publicBaseUrl());
        serve(registry, props.uploads().chatAttachment().dir(),
                props.uploads().chatAttachment().publicBaseUrl());
    }

    private static void serve(ResourceHandlerRegistry registry, String dir, String publicBaseUrl) {
        Path absolute = Paths.get(dir).toAbsolutePath().normalize();
        String pattern = trimTrailingSlash(publicBaseUrl) + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations("file:" + absolute + "/");
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
