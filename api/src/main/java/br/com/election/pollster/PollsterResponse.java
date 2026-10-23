package br.com.election.pollster;
import java.util.UUID;
public record PollsterResponse(UUID id, String name, String slug, String websiteUrl, boolean active) {
    public static PollsterResponse from(Pollster p) { return new PollsterResponse(p.getId(), p.getName(), p.getSlug(), p.getWebsiteUrl(), p.getActive()); }
}
