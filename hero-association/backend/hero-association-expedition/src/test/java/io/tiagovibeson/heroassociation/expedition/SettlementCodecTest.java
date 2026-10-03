package io.tiagovibeson.heroassociation.expedition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.tiagovibeson.heroassociation.contract.QuestContract.*;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.UuidV7;

class SettlementCodecTest {
    @Test
    void restartWithDifferentSetOrderRetainsSettlementBytesAndAcknowledgmentDigest() throws Exception {
        UUID firstMap = UUID.fromString("019c4c00-0006-7000-8000-000000000001");
        UUID secondMap = UUID.fromString("019c4c00-0006-7000-8000-000000000002");
        UUID expedition = UuidV7.next(), manager = UuidV7.next(), agency = UuidV7.next();
        var definition = new Definition(UuidV7.next(), 1, "Two destinations", "Progress on either Map",
                Objective.KILL_COUNT, UuidV7.next(), 3, Set.of(firstMap, secondMap), new Reward(120, Map.of(), Map.of()));
        var assignment = new Assignment(UuidV7.next(), manager, definition, 0, "ACTIVE");
        var pin = new Pin(expedition, manager, agency, firstMap, 1, assignment, true);
        var skills = new EnumMap<HeroSkill, BigDecimal>(HeroSkill.class);
        for (HeroSkill skill : HeroSkill.values()) skills.put(skill, new BigDecimal("0.000000"));
        skills.put(HeroSkill.MELEE, new BigDecimal("123.123456"));
        var heroes = List.of(new SettlementEnvelope.HeroFinal(UuidV7.next(), HeroClass.WARRIOR, 0, skills, 300, 50, 1000));
        UUID party = UuidV7.next();
        var envelope = new SettlementEnvelope(2, expedition, manager, agency, party, heroes, 0,
                Map.of(), Map.of(), firstMap, 1, new Progress(pin, 1));
        var beforeRestart = new SettlementCodec(mapperWithMapOrder(List.of(firstMap, secondMap)));
        var afterRestart = new SettlementCodec(mapperWithMapOrder(List.of(secondMap, firstMap)));
        byte[] original = beforeRestart.encode(envelope);
        byte[] retried = afterRestart.encode(envelope);
        assertArrayEquals(original, retried);
        assertNotEquals(beforeRestart.digest(original), afterRestart.digest(afterRestart.encode(
                new SettlementEnvelope(2, expedition, manager, agency, party, heroes, 0,
                        Map.of(), Map.of(), firstMap, 1, new Progress(pin, 2)))));
        var withoutQuest = new SettlementEnvelope(2, expedition, manager, agency, party, heroes, 0,
                Map.of(), Map.of(), firstMap, 1, null);
        assertArrayEquals(new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsBytes(withoutQuest), beforeRestart.encode(withoutQuest));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ObjectMapper mapperWithMapOrder(List<UUID> order) {
        var module = new SimpleModule();
        module.addSerializer(Set.class, new JsonSerializer<Set>() {
            @Override public void serialize(Set value, JsonGenerator output, SerializerProvider provider) throws IOException {
                output.writeStartArray();
                for (UUID id : order) output.writeString(id.toString());
                output.writeEndArray();
            }
        });
        return new ObjectMapper().registerModule(module);
    }
}
