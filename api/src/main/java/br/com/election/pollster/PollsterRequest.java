package br.com.election.pollster;
import jakarta.validation.constraints.*;
import br.com.election.common.HttpUrl;
public record PollsterRequest(@NotBlank @Size(max=200) String name,
    @NotBlank @Size(max=200) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*", message="use letras minúsculas, números e hífens") String slug,
    @HttpUrl @Size(max=2000) String websiteUrl, Boolean active) {}
