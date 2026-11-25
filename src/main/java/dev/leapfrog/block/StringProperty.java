package dev.leapfrog.block;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.state.properties.Property;

/** String-valued property, so 26.x properties without a 1.21.11 enum (potent_sulfur_state...) still work. */
public final class StringProperty extends Property<String> {
    private final List<String> values;

    public StringProperty(String name, List<String> values) {
        super(name, String.class);
        this.values = List.copyOf(values);
    }

    @Override
    public List<String> getPossibleValues() {
        return values;
    }

    @Override
    public String getName(String value) {
        return value;
    }

    @Override
    public Optional<String> getValue(String name) {
        return values.contains(name) ? Optional.of(name) : Optional.empty();
    }

    @Override
    public int getInternalIndex(String value) {
        return values.indexOf(value);
    }
}
