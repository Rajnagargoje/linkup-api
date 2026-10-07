package com.linkup.user.randomai;

import java.util.*;

/** Fictional companions, deliberately not User records or real people's photographs. */
public record AiPersona(String id, String name, String color, String interests, String style, Profile profile) {
    public record Profile(String gender, int age, String city, String education) {}
    public static final List<AiPersona> ALL = List.of(
        new AiPersona("aanya", "Aanya", "violet", "music, food, travel", "Warm, relaxed, lightly playful; never pushy.", new Profile("f", 24, "Pune", "B.Com")),
        new AiPersona("kabir", "Kabir", "teal", "movies, cricket, technology", "Easygoing, thoughtful, enjoys light banter.", new Profile("m", 26, "Hyderabad", "B.Tech")),
        new AiPersona("tara", "Tara", "rose", "books, art, travel", "Creative, calm, casual and attentive.", new Profile("f", 25, "Bengaluru", "BA in English")));
    public static AiPersona find(String id) { return ALL.stream().filter(p -> p.id.equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("AI character not found.")); }
    public static AiPersona choose(Collection<String> interests, String previous) {
        return ALL.stream().filter(p -> !p.id.equals(previous)).max(Comparator.comparingInt(p ->
            (int) interests.stream().filter(i -> p.interests.contains(i.toLowerCase(Locale.ROOT))).count())).orElse(ALL.get(0));
    }
    public String profileLabel() { return profile.gender() + " · " + profile.age() + " · " + profile.city(); }
}
