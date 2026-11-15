package io.github.topdanya33200_netizen.reporthook.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.topdanya33200_netizen.reporthook.TestReports;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportEmbedFactoryTest {

    private static final WebhookUrl WEBHOOK =
            WebhookUrl.parse("https://discord.com/api/webhooks/123456789/AbCdEfGhIjKlMnOpQrStUvWxYz_0123").orElseThrow();

    private static DiscordSettings settings(String roleId, boolean forum) {
        return new DiscordSettings(true, WEBHOOK, roleId, "ReportHook", "", DiscordSettings.DEFAULT_THUMBNAIL,
                true, forum, true, true, Map.of());
    }

    private static ReportEmbedFactory factory(DiscordSettings settings) {
        return new ReportEmbedFactory(settings, DiscordLabels.defaults(), "Survival", ZoneOffset.UTC);
    }

    @Test
    void newReportPingsOnlyTheConfiguredRole() {
        JsonObject payload = factory(settings("424242424242", false)).created(TestReports.report(7, ReportStatus.OPEN));
        assertEquals("<@&424242424242>", payload.get("content").getAsString());
        JsonObject allowed = payload.getAsJsonObject("allowed_mentions");
        assertEquals(0, allowed.getAsJsonArray("parse").size());
        assertEquals("424242424242", allowed.getAsJsonArray("roles").get(0).getAsString());
    }

    @Test
    void updatesNeverPing() {
        JsonObject payload = factory(settings("424242424242", false)).updated(TestReports.report(7, ReportStatus.CLAIMED));
        assertFalse(payload.has("content"));
        assertFalse(payload.getAsJsonObject("allowed_mentions").has("roles"));
        assertEquals(0, payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
    }

    @Test
    void embedReflectsTheStatus() {
        ReportEmbedFactory factory = factory(settings(null, false));
        JsonObject open = embed(factory.created(TestReports.report(7, ReportStatus.OPEN)));
        JsonObject resolved = embed(factory.updated(TestReports.report(7, ReportStatus.RESOLVED)));
        assertEquals(DiscordSettings.defaultColor(ReportStatus.OPEN), open.get("color").getAsInt());
        assertEquals(DiscordSettings.defaultColor(ReportStatus.RESOLVED), resolved.get("color").getAsInt());
        assertEquals("🟢 Resolved by Mod", field(resolved, "Status"));
        assertTrue(open.get("title").getAsString().startsWith("Report #7"));
    }

    @Test
    void includesLocationChatAndThumbnail() {
        JsonObject embed = embed(factory(settings(null, false)).created(TestReports.report(7, ReportStatus.OPEN)));
        assertEquals("`world` 120, 64, -31", field(embed, "Location"));
        assertTrue(field(embed, "Recent chat").contains("[19:29:55] ez"));
        assertEquals("https://mc-heads.net/avatar/" + TestReports.STEVE + "/64",
                embed.getAsJsonObject("thumbnail").get("url").getAsString());
        assertEquals("2026-11-15T19:30:00Z", embed.get("timestamp").getAsString());
    }

    @Test
    void escapesTheReason() {
        Report base = TestReports.report(7, ReportStatus.OPEN);
        Report sneaky = new Report(base.id(), base.reporterId(), base.reporterName(), base.targetId(), base.targetName(),
                "@everyone **free** [nitro](https://evil.example)", base.serverName(), base.location(), base.chatContext(),
                base.status(), null, null, null, null, null, base.createdAt(), base.updatedAt());
        String description = embed(factory(settings(null, false)).created(sneaky)).get("description").getAsString();
        assertFalse(description.contains("@everyone"));
        assertFalse(description.contains("**free**"));
        assertFalse(description.contains("](https"));
    }

    @Test
    void forumModeNamesTheThread() {
        Report report = TestReports.report(7, ReportStatus.OPEN);
        assertEquals("#7 Steve - Kill aura", factory(settings(null, true)).threadName(report));
        assertNull(factory(settings(null, false)).threadName(report));
    }

    private static JsonObject embed(JsonObject payload) {
        return payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
    }

    private static String field(JsonObject embed, String name) {
        JsonArray fields = embed.getAsJsonArray("fields");
        for (JsonElement element : fields) {
            JsonObject field = element.getAsJsonObject();
            if (field.get("name").getAsString().equals(name)) {
                return field.get("value").getAsString();
            }
        }
        throw new AssertionError("No field " + name + " in " + embed);
    }
}
