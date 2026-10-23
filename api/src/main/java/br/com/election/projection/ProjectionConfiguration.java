package br.com.election.projection;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@Configuration @EnableConfigurationProperties(ProjectionProperties.class)
public class ProjectionConfiguration {}
