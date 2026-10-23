package br.com.election.tse;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@Configuration @EnableConfigurationProperties(TseResultsProperties.class)
public class TseConfiguration {}
