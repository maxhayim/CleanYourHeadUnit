package io.github.justinnewman.gameday;

/** One calendar the person added: usually one team. */
final class Calendar {
    /** Links.id(link): stable, so a cached copy survives the setup script replacing the list. */
    final String id;
    final String name;
    /** A secret: never logged or shown in full. */
    final String link;
    final Sport sport;
    /** A Palette index. */
    final int colour;

    Calendar(String id, String name, String link, Sport sport, int colour) {
        this.id = id;
        this.name = name;
        this.link = link;
        this.sport = sport;
        this.colour = colour;
    }

    @Override
    public String toString() {
        return name + " (" + Links.mask(link) + ")";
    }
}
