package com.MessTech.common.command.kill;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;

/**
 * Parses one 1.20.1 entity selector token ({@code @e[type=zombie,limit=3]}, {@code Steve}, a player UUID, ...) into a
 * {@link MTEntitySelector}. This is the 1.7.10 port of {@code EntitySelectorParser} plus its option table from
 * {@code EntitySelectorOptions}: the same defaults per selector type, the same option names and inversion rules, and
 * the same errors.
 * <p>
 * Differences forced by the target version, all of them deliberate:
 * <ul>
 * <li>{@code tag=} has no 1.7.10 equivalent (entities have no scoreboard tags) and neither do {@code advancements=}
 * or {@code predicate=}; they fail with {@code argument.entity.options.unsupported} instead of silently matching
 * nothing.</li>
 * <li>{@code type=#tag} (entity type tags) does not exist in 1.7.10 either, so it fails the same way.</li>
 * <li>{@code nbt=} is parsed by 1.7.10's {@link JsonToNBT} (the {@code /testforblock} grammar) and compared against
 * {@link Entity#writeToNBT}, extended with the {@code SelectedItem} compound that 1.20.1 adds for players.</li>
 * <li>The 1.7.10 spellings are accepted next to the 1.20.1 ones: the positional {@code @p[x,y,z,r]} list and
 * {@code r=} / {@code rm=} / {@code c=} / {@code m=} / {@code l=} / {@code lm=} / {@code score_<objective>=}.</li>
 * </ul>
 * The parser works on the command's space-split argument, so unlike 1.20.1 a selector cannot contain spaces
 * ({@code nbt={Health: 20.0f}} has to be written without the space).
 */
public class MTEntitySelectorParser {

    private final String input;
    private int cursor;

    private int maxResults;
    private boolean includesEntities;
    private boolean worldLimited;
    private MTEntitySelector.DoubleRange distance;
    private MTEntitySelector.IntRange level;
    private Double x;
    private Double y;
    private Double z;
    private Double deltaX;
    private Double deltaY;
    private Double deltaZ;
    private MTEntitySelector.WrappedRange rotX;
    private MTEntitySelector.WrappedRange rotY;
    private Predicate<Entity> predicate = entity -> true;
    private int order = MTEntitySelector.ORDER_ARBITRARY;
    private boolean currentEntity;
    private String playerName;
    private UUID entityUUID;

    private boolean hasNameEquals;
    private boolean hasNameNotEquals;
    private boolean isLimited;
    private boolean isSorted;
    private boolean hasGamemodeEquals;
    private boolean hasGamemodeNotEquals;
    private boolean hasTeamEquals;
    private boolean hasTeamNotEquals;
    private boolean hasScores;
    private boolean typeLimited;
    private boolean typeInverse;
    private int positionalIndex;

    public MTEntitySelectorParser(String input) {
        this.input = input == null ? "" : input;
    }

    public MTEntitySelector parse() {
        if (canRead() && peek() == '@') {
            skip();
            parseSelector();
        } else {
            parseNameOrUUID();
        }
        finalizePredicates();
        if (canRead()) {
            throw new CommandException("argument.entity.options.unterminated");
        }
        return getSelector();
    }

    // ------------------------------------------------------------------ selector head

    private void parseSelector() {
        if (!canRead()) {
            throw new CommandException("argument.entity.selector.missing");
        }
        int start = getCursor();
        char selector = read();
        if (selector == 'p') {
            this.maxResults = 1;
            this.includesEntities = false;
            this.order = MTEntitySelector.ORDER_NEAREST;
        } else if (selector == 'a') {
            this.maxResults = MTEntitySelector.INFINITE;
            this.includesEntities = false;
            this.order = MTEntitySelector.ORDER_ARBITRARY;
        } else if (selector == 'r') {
            this.maxResults = 1;
            this.includesEntities = false;
            this.order = MTEntitySelector.ORDER_RANDOM;
        } else if (selector == 's') {
            this.maxResults = 1;
            this.includesEntities = true;
            this.currentEntity = true;
        } else if (selector == 'e') {
            this.maxResults = MTEntitySelector.INFINITE;
            this.includesEntities = true;
            this.order = MTEntitySelector.ORDER_ARBITRARY;
            addPredicate(Entity::isEntityAlive);
        } else {
            setCursor(start);
            throw new CommandException("argument.entity.selector.unknown", "@" + selector);
        }
        if (canRead() && peek() == '[') {
            skip();
            parseOptions();
        }
    }

    private void parseNameOrUUID() {
        int start = getCursor();
        String name = readString();
        try {
            this.entityUUID = UUID.fromString(name);
            this.includesEntities = true;
        } catch (IllegalArgumentException ignored) {
            if (name.isEmpty() || name.length() > 16) {
                setCursor(start);
                throw new CommandException("argument.entity.invalid");
            }
            this.includesEntities = false;
            this.playerName = name;
        }
        this.maxResults = 1;
    }

    // ------------------------------------------------------------------ options

    private void parseOptions() {
        skipWhitespace();
        while (canRead() && peek() != ']') {
            skipWhitespace();
            int optionStart = getCursor();
            String key = readString();
            skipWhitespace();
            if (!canRead() || peek() != '=') {
                // 1.7.10's positional @p[x,y,z,r] list: no "key=", just a value.
                setCursor(optionStart);
                applyPositional(optionStart, readLegacyValue());
            } else {
                skip();
                skipWhitespace();
                applyOption(optionStart, key);
            }
            skipWhitespace();
            if (canRead()) {
                if (peek() == ',') {
                    skip();
                    continue;
                }
                if (peek() == ']') {
                    break;
                }
                throw new CommandException("argument.entity.options.unterminated");
            }
        }
        if (!canRead()) {
            throw new CommandException("argument.entity.options.unterminated");
        }
        skip();
    }

    private void applyOption(int optionStart, String key) {
        switch (key) {
            case "name":
                optionName();
                return;
            case "distance":
                optionDistance();
                return;
            case "level":
                optionLevel();
                return;
            case "x":
                this.x = optionCoordinate("x", this.x);
                return;
            case "y":
                this.y = optionCoordinate("y", this.y);
                return;
            case "z":
                this.z = optionCoordinate("z", this.z);
                return;
            case "dx":
                this.deltaX = optionCoordinate("dx", this.deltaX);
                return;
            case "dy":
                this.deltaY = optionCoordinate("dy", this.deltaY);
                return;
            case "dz":
                this.deltaZ = optionCoordinate("dz", this.deltaZ);
                return;
            case "x_rotation":
                if (this.rotX != null) throw inapplicable("x_rotation");
                this.rotX = readWrappedRange();
                return;
            case "y_rotation":
                if (this.rotY != null) throw inapplicable("y_rotation");
                this.rotY = readWrappedRange();
                return;
            case "limit":
                optionLimit();
                return;
            case "sort":
                optionSort();
                return;
            case "gamemode":
                optionGamemode();
                return;
            case "team":
                optionTeam();
                return;
            case "type":
                optionType();
                return;
            case "tag":
                throw unsupported("tag");
            case "nbt":
                optionNbt(optionStart);
                return;
            case "scores":
                optionScores();
                return;
            case "advancements":
                throw unsupported("advancements");
            case "predicate":
                throw unsupported("predicate");
            // 1.7.10 PlayerSelector spellings (the user asked for both syntaxes to work).
            case "r":
                setDistanceMax(readDouble());
                return;
            case "rm":
                setDistanceMin(readDouble());
                return;
            case "c":
                optionLegacyCount();
                return;
            case "m":
                optionLegacyGamemode();
                return;
            case "l":
                setLevelMax(readInt());
                return;
            case "lm":
                setLevelMin(readInt());
                return;
            default:
                if (key.startsWith("score_") && key.length() > "score_".length()) {
                    optionLegacyScore(key.substring("score_".length()));
                    return;
                }
                setCursor(optionStart);
                throw new CommandException("argument.entity.options.unknown", key);
        }
    }

    private void applyPositional(int optionStart, String rawValue) {
        String value = rawValue.trim();
        int index = this.positionalIndex++;
        if (index > 3 || value.isEmpty()) {
            return;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            setCursor(optionStart);
            throw new CommandException("commands.generic.num.invalid", value);
        }
        if (index == 0) {
            this.x = parsed;
        } else if (index == 1) {
            this.y = parsed;
        } else if (index == 2) {
            this.z = parsed;
        } else {
            setDistanceMax(parsed);
        }
        this.worldLimited = true;
    }

    // ------------------------------------------------------------------ 1.20.1 option handlers

    private void optionName() {
        boolean invert = shouldInvertValue();
        String name = readString();
        if (this.hasNameNotEquals && !invert) throw inapplicable("name");
        if (invert) {
            this.hasNameNotEquals = true;
        } else {
            this.hasNameEquals = true;
        }
        addPredicate(entity -> matchesName(entity, name) != invert);
    }

    private void optionDistance() {
        MTEntitySelector.DoubleRange range = readDoubleRange();
        if ((range.min != null && range.min < 0) || (range.max != null && range.max < 0)) {
            throw new CommandException("argument.entity.options.distance.negative");
        }
        if (this.distance != null) throw inapplicable("distance");
        this.distance = range;
        this.worldLimited = true;
    }

    private void optionLevel() {
        MTEntitySelector.IntRange range = readIntRange();
        if ((range.min != null && range.min < 0) || (range.max != null && range.max < 0)) {
            throw new CommandException("argument.entity.options.level.negative");
        }
        if (this.level != null) throw inapplicable("level");
        this.level = range;
        this.includesEntities = false;
    }

    private Double optionCoordinate(String option, Double current) {
        if (current != null) throw inapplicable(option);
        double value = readDouble();
        this.worldLimited = true;
        return value;
    }

    private void optionLimit() {
        if (this.currentEntity || this.isLimited) throw inapplicable("limit");
        int value = readInt();
        if (value < 1) {
            throw new CommandException("argument.entity.options.limit.toosmall");
        }
        this.maxResults = value;
        this.isLimited = true;
    }

    private void optionSort() {
        if (this.currentEntity || this.isSorted) throw inapplicable("sort");
        String value = readUnquotedString();
        if ("nearest".equals(value)) {
            this.order = MTEntitySelector.ORDER_NEAREST;
        } else if ("furthest".equals(value)) {
            this.order = MTEntitySelector.ORDER_FURTHEST;
        } else if ("random".equals(value)) {
            this.order = MTEntitySelector.ORDER_RANDOM;
        } else if ("arbitrary".equals(value)) {
            this.order = MTEntitySelector.ORDER_ARBITRARY;
        } else {
            throw new CommandException("argument.entity.options.sort.irreversible", value);
        }
        this.isSorted = true;
    }

    private void optionGamemode() {
        if (this.hasGamemodeEquals) throw inapplicable("gamemode");
        boolean invert = shouldInvertValue();
        if (this.hasGamemodeNotEquals && !invert) throw inapplicable("gamemode");
        String value = readUnquotedString();
        WorldSettings.GameType gameType = WorldSettings.GameType.getByName(value.toLowerCase(Locale.ROOT));
        if (gameType == null || gameType == WorldSettings.GameType.NOT_SET) {
            throw new CommandException("argument.entity.options.mode.invalid", value);
        }
        this.includesEntities = false;
        final WorldSettings.GameType wanted = gameType;
        addPredicate(entity -> {
            if (!(entity instanceof EntityPlayerMP)) return false;
            WorldSettings.GameType actual = ((EntityPlayerMP) entity).theItemInWorldManager.getGameType();
            return (actual == wanted) != invert;
        });
        if (invert) {
            this.hasGamemodeNotEquals = true;
        } else {
            this.hasGamemodeEquals = true;
        }
    }

    private void optionTeam() {
        if (this.hasTeamEquals) throw inapplicable("team");
        boolean invert = shouldInvertValue();
        String value = readUnquotedString();
        addPredicate(entity -> {
            if (!(entity instanceof EntityLivingBase)) return false;
            Team team = ((EntityLivingBase) entity).getTeam();
            String teamName = team == null ? "" : team.getRegisteredName();
            return teamName.equals(value) != invert;
        });
        if (invert) {
            this.hasTeamNotEquals = true;
        } else {
            this.hasTeamEquals = true;
        }
    }

    private void optionType() {
        if (this.typeLimited) throw inapplicable("type");
        boolean invert = shouldInvertValue();
        if (this.typeInverse && !invert) throw inapplicable("type");
        if (invert) this.typeInverse = true;
        if (isTag()) throw unsupported("type");
        String raw = readResourceLocation();
        Class<? extends Entity> entityClass = resolveEntityType(raw);
        if (entityClass == null) {
            throw new CommandException("argument.entity.options.type.invalid", raw);
        }
        if (!invert && entityClass == EntityPlayerMP.class) {
            this.includesEntities = false;
        }
        final Class<? extends Entity> wanted = entityClass;
        addPredicate(
            entity -> (wanted == EntityPlayerMP.class ? entity instanceof EntityPlayerMP : entity.getClass() == wanted)
                != invert);
        if (!invert) this.typeLimited = true;
    }

    private void optionNbt(int optionStart) {
        boolean invert = shouldInvertValue();
        String nbtText = readCompound();
        NBTTagCompound expected;
        try {
            NBTBase tag = JsonToNBT.func_150315_a(nbtText);
            if (!(tag instanceof NBTTagCompound)) {
                throw new CommandException("argument.entity.options.nbt.invalid", nbtText);
            }
            expected = (NBTTagCompound) tag;
        } catch (NBTException exception) {
            setCursor(optionStart);
            throw new CommandException("argument.entity.options.nbt.invalid", exception.getMessage());
        }
        final NBTTagCompound wanted = expected;
        addPredicate(entity -> matchesNbt(entity, wanted) != invert);
    }

    private void optionScores() {
        if (this.hasScores) throw inapplicable("scores");
        Map<String, MTEntitySelector.IntRange> filters = new LinkedHashMap<>();
        expect('{', "scores");
        skipWhitespace();
        while (canRead() && peek() != '}') {
            skipWhitespace();
            String objective = readUnquotedString();
            if (objective.isEmpty()) {
                throw new CommandException("argument.entity.options.valueless", "scores");
            }
            skipWhitespace();
            expect('=', "scores");
            skipWhitespace();
            filters.put(objective, readIntRange());
            skipWhitespace();
            if (canRead() && peek() == ',') skip();
        }
        expect('}', "scores");
        if (!filters.isEmpty()) {
            final Map<String, MTEntitySelector.IntRange> wanted = filters;
            final Scoreboard scoreboard = serverScoreboard();
            addPredicate(entity -> matchesScores(scoreboard, entity, wanted));
        }
        this.hasScores = true;
    }

    // ------------------------------------------------------------------ 1.7.10 option handlers

    private void optionLegacyCount() {
        int value = readInt();
        this.maxResults = value > 0 ? value : MTEntitySelector.INFINITE;
        this.isLimited = true;
    }

    private void optionLegacyGamemode() {
        int id = readInt();
        if (id == -1) return;
        WorldSettings.GameType gameType = WorldSettings.GameType.getByID(id);
        if (gameType == null || gameType == WorldSettings.GameType.NOT_SET) {
            throw new CommandException("argument.entity.options.mode.invalid", String.valueOf(id));
        }
        this.includesEntities = false;
        addPredicate(
            entity -> entity instanceof EntityPlayerMP
                && ((EntityPlayerMP) entity).theItemInWorldManager.getGameType() == gameType);
        this.hasGamemodeEquals = true;
    }

    private void optionLegacyScore(String objective) {
        int minimum = readInt();
        final Scoreboard scoreboard = serverScoreboard();
        addPredicate(entity -> {
            if (!(entity instanceof EntityPlayerMP)) return false;
            Score score = findScore(scoreboard, entity.getCommandSenderName(), objective);
            return score != null && score.getScorePoints() >= minimum;
        });
    }

    private void setDistanceMax(double max) {
        if (max < 0) throw new CommandException("argument.entity.options.distance.negative");
        Double min = this.distance == null ? null : this.distance.min;
        this.distance = new MTEntitySelector.DoubleRange(min, max);
        this.worldLimited = true;
    }

    private void setDistanceMin(double min) {
        if (min < 0) throw new CommandException("argument.entity.options.distance.negative");
        Double max = this.distance == null ? null : this.distance.max;
        this.distance = new MTEntitySelector.DoubleRange(min, max);
        this.worldLimited = true;
    }

    private void setLevelMax(int max) {
        Integer min = this.level == null ? null : this.level.min;
        this.level = new MTEntitySelector.IntRange(min, max);
        this.includesEntities = false;
    }

    private void setLevelMin(int min) {
        Integer max = this.level == null ? null : this.level.max;
        this.level = new MTEntitySelector.IntRange(min, max);
        this.includesEntities = false;
    }

    // ------------------------------------------------------------------ predicates

    private void finalizePredicates() {
        if (this.rotX != null) {
            final MTEntitySelector.WrappedRange range = this.rotX;
            addPredicate(entity -> range.matches(entity.rotationPitch));
        }
        if (this.rotY != null) {
            final MTEntitySelector.WrappedRange range = this.rotY;
            addPredicate(entity -> range.matches(entity.rotationYaw));
        }
        if (this.level != null) {
            final MTEntitySelector.IntRange range = this.level;
            addPredicate(
                entity -> entity instanceof EntityPlayerMP && range.matches(((EntityPlayerMP) entity).experienceLevel));
        }
    }

    private void addPredicate(Predicate<Entity> extra) {
        this.predicate = this.predicate.and(extra);
    }

    private static boolean matchesName(Entity entity, String name) {
        if (entity.getCommandSenderName()
            .equals(name)) return true;
        if (entity instanceof EntityPlayer) return false;
        if (entity instanceof EntityLiving && ((EntityLiving) entity).hasCustomNameTag()) return false;
        // 1.7.10 has no per-entity type description on the wire, but the registry name is the stable spelling
        // 1.20.1 users type ("Zombie" / "zombie").
        String id = EntityList.getEntityString(entity);
        return id != null && id.equalsIgnoreCase(name);
    }

    private static boolean matchesNbt(Entity entity, NBTTagCompound expected) {
        NBTTagCompound actual = new NBTTagCompound();
        entity.writeToNBT(actual);
        if (entity instanceof EntityPlayerMP) {
            ItemStack held = ((EntityPlayerMP) entity).getHeldItem();
            if (held != null) actual.setTag("SelectedItem", held.writeToNBT(new NBTTagCompound()));
        }
        return compareNbt(expected, actual);
    }

    /** 1.20.1's {@code NbtUtils.compareNbt(expected, actual, true)}: compounds partially, lists unordered. */
    private static boolean compareNbt(NBTBase expected, NBTBase actual) {
        if (expected == actual) return true;
        if (expected == null) return true;
        if (actual == null) return false;
        if (!expected.getClass()
            .equals(actual.getClass())) return false;
        if (expected instanceof NBTTagCompound) {
            NBTTagCompound expectedCompound = (NBTTagCompound) expected;
            NBTTagCompound actualCompound = (NBTTagCompound) actual;
            for (String key : expectedCompound.func_150296_c()) {
                if (!compareNbt(expectedCompound.getTag(key), actualCompound.getTag(key))) return false;
            }
            return true;
        }
        if (expected instanceof NBTTagList) {
            NBTTagList expectedList = (NBTTagList) expected;
            NBTTagList actualList = (NBTTagList) actual;
            // 1.7.10 NBTTagList has no generic index getter (only getCompoundTagAt), so compound elements - the
            // inventory/Pos-like lists the option is meant for - are matched unordered like 1.20.1, while lists of
            // primitives fall back to full equality.
            if (expectedList.func_150303_d() != 10 || actualList.func_150303_d() != 10) {
                return expectedList.equals(actualList);
            }
            if (expectedList.tagCount() == 0) return actualList.tagCount() == 0;
            for (int i = 0; i < expectedList.tagCount(); i++) {
                NBTTagCompound element = expectedList.getCompoundTagAt(i);
                boolean matched = false;
                for (int j = 0; j < actualList.tagCount(); j++) {
                    if (compareNbt(element, actualList.getCompoundTagAt(j))) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
            return true;
        }
        return expected.equals(actual);
    }

    private static boolean matchesScores(Scoreboard scoreboard, Entity entity,
        Map<String, MTEntitySelector.IntRange> filters) {
        String name = entity instanceof EntityPlayerMP ? entity.getCommandSenderName()
            : entity.getUniqueID()
                .toString();
        for (Map.Entry<String, MTEntitySelector.IntRange> entry : filters.entrySet()) {
            Score score = findScore(scoreboard, name, entry.getKey());
            if (score == null || !entry.getValue()
                .matches(score.getScorePoints())) return false;
        }
        return true;
    }

    /** The server-wide scoreboard, the one 1.7.10's own {@code /scoreboard} command reads as well. */
    private static Scoreboard serverScoreboard() {
        return MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getScoreboard();
    }

    private static Score findScore(Scoreboard scoreboard, String playerName, String objectiveName) {
        ScoreObjective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return null;
        for (Score score : scoreboard.func_96534_i(objective)) {
            if (score.getPlayerName()
                .equals(playerName)) return score;
        }
        return null;
    }

    // ------------------------------------------------------------------ entity type names

    private static Class<? extends Entity> resolveEntityType(String raw) {
        String path = raw;
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        String wanted = normalizeTypeName(path);
        if (wanted.isEmpty()) return null;
        if ("player".equals(wanted)) return EntityPlayerMP.class;
        for (Map.Entry<String, Class<? extends Entity>> entry : EntityList.stringToClassMapping.entrySet()) {
            if (normalizeTypeName(entry.getKey()).equals(wanted)) return entry.getValue();
        }
        return null;
    }

    /** {@code PigZombie} / {@code pig_zombie} / {@code minecraft:pig_zombie} all normalise to {@code pigzombie}. */
    private static String normalizeTypeName(String name) {
        return name.toLowerCase(Locale.ROOT)
            .replace("_", "");
    }

    // ------------------------------------------------------------------ building the selector

    private MTEntitySelector getSelector() {
        double[] box = null;
        if (this.deltaX != null || this.deltaY != null || this.deltaZ != null) {
            double dX = this.deltaX == null ? 0.0D : this.deltaX;
            double dY = this.deltaY == null ? 0.0D : this.deltaY;
            double dZ = this.deltaZ == null ? 0.0D : this.deltaZ;
            boolean negativeX = dX < 0.0D;
            boolean negativeY = dY < 0.0D;
            boolean negativeZ = dZ < 0.0D;
            box = new double[] { negativeX ? dX : 0.0D, negativeY ? dY : 0.0D, negativeZ ? dZ : 0.0D,
                (negativeX ? 0.0D : dX) + 1.0D, (negativeY ? 0.0D : dY) + 1.0D, (negativeZ ? 0.0D : dZ) + 1.0D };
        }
        final Double finalX = this.x;
        final Double finalY = this.y;
        final Double finalZ = this.z;
        Function<MTEntitySelector.Pos, MTEntitySelector.Pos> position = finalX == null && finalY == null
            && finalZ == null
                ? pos -> pos
                : pos -> new MTEntitySelector.Pos(
                    finalX == null ? pos.x : finalX,
                    finalY == null ? pos.y : finalY,
                    finalZ == null ? pos.z : finalZ);
        return new MTEntitySelector(
            this.maxResults,
            this.includesEntities,
            this.worldLimited,
            this.predicate,
            this.distance,
            position,
            box,
            this.order,
            this.currentEntity,
            this.playerName,
            this.entityUUID);
    }

    // ------------------------------------------------------------------ low level reader

    private boolean canRead() {
        return this.cursor < this.input.length();
    }

    private boolean canRead(int length) {
        return this.cursor + length <= this.input.length();
    }

    private char peek() {
        return this.input.charAt(this.cursor);
    }

    private char peek(int offset) {
        return this.input.charAt(this.cursor + offset);
    }

    private char read() {
        return this.input.charAt(this.cursor++);
    }

    private void skip() {
        this.cursor++;
    }

    private int getCursor() {
        return this.cursor;
    }

    private void setCursor(int cursor) {
        this.cursor = cursor;
    }

    private void skipWhitespace() {
        while (canRead() && Character.isWhitespace(peek())) skip();
    }

    private String readString() {
        if (canRead() && (peek() == '"' || peek() == '\'')) {
            char quote = read();
            StringBuilder builder = new StringBuilder();
            while (canRead() && peek() != quote) {
                char character = read();
                if (character == '\\') {
                    if (!canRead()) break;
                    char escaped = read();
                    if (escaped == 'n') {
                        builder.append('\n');
                    } else if (escaped == 't') {
                        builder.append('\t');
                    } else if (escaped == 'r') {
                        builder.append('\r');
                    } else {
                        builder.append(escaped);
                    }
                } else {
                    builder.append(character);
                }
            }
            if (!canRead()) {
                throw new CommandException("argument.entity.options.unterminated");
            }
            skip();
            return builder.toString();
        }
        return readUnquotedString();
    }

    private String readUnquotedString() {
        int start = getCursor();
        while (canRead() && isAllowedInUnquotedString(peek())) skip();
        return this.input.substring(start, getCursor());
    }

    private static boolean isAllowedInUnquotedString(char character) {
        return character >= '0' && character <= '9' || character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character == '_'
            || character == '-'
            || character == '.'
            || character == '+';
    }

    /** The 1.7.10 {@code ResourceLocation} style name used by {@code type=}; includes the namespace colon. */
    private String readResourceLocation() {
        int start = getCursor();
        while (canRead()) {
            char character = peek();
            boolean allowed = character >= '0' && character <= '9' || character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character == '_'
                || character == '-'
                || character == '.'
                || character == ':'
                || character == '/';
            if (!allowed) break;
            skip();
        }
        return this.input.substring(start, getCursor());
    }

    private String readNumber() {
        int start = getCursor();
        while (canRead()) {
            char character = peek();
            if (character >= '0' && character <= '9' || character == '-') {
                skip();
            } else if (character == '.') {
                if (canRead(2) && peek(1) == '.') break;
                skip();
            } else {
                break;
            }
        }
        return this.input.substring(start, getCursor());
    }

    private Double readOptionalDouble() {
        String number = readNumber();
        return number.isEmpty() ? null : parseDouble(number);
    }

    private Float readOptionalFloat() {
        String number = readNumber();
        if (number.isEmpty()) return null;
        try {
            return Float.parseFloat(number);
        } catch (NumberFormatException exception) {
            throw new CommandException("commands.generic.num.invalid", number);
        }
    }

    private double readDouble() {
        String number = readNumber();
        return parseDouble(number);
    }

    private double parseDouble(String number) {
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException exception) {
            throw new CommandException("commands.generic.num.invalid", number);
        }
    }

    private int readInt() {
        String number = readNumber();
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            throw new CommandException("commands.generic.num.invalid", number);
        }
    }

    private MTEntitySelector.DoubleRange readDoubleRange() {
        if (!canRead()) throw new CommandException("argument.range.empty");
        Double min = readOptionalDouble();
        Double max;
        if (canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            max = readOptionalDouble();
        } else {
            max = min;
        }
        if (min == null && max == null) throw new CommandException("argument.range.empty");
        if (min != null && max != null && min > max) throw new CommandException("argument.range.swapped");
        return new MTEntitySelector.DoubleRange(min, max);
    }

    private MTEntitySelector.IntRange readIntRange() {
        if (!canRead()) throw new CommandException("argument.range.empty");
        Integer min = readOptionalInt();
        Integer max;
        if (canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            max = readOptionalInt();
        } else {
            max = min;
        }
        if (min == null && max == null) throw new CommandException("argument.range.empty");
        if (min != null && max != null && min > max) throw new CommandException("argument.range.swapped");
        return new MTEntitySelector.IntRange(min, max);
    }

    private Integer readOptionalInt() {
        String number = readNumber();
        if (number.isEmpty()) return null;
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException exception) {
            throw new CommandException("commands.generic.num.invalid", number);
        }
    }

    private MTEntitySelector.WrappedRange readWrappedRange() {
        if (!canRead()) throw new CommandException("argument.range.empty");
        Float min = readOptionalFloat();
        Float max;
        if (canRead(2) && peek() == '.' && peek(1) == '.') {
            skip();
            skip();
            max = readOptionalFloat();
        } else {
            max = min;
        }
        if (min == null && max == null) throw new CommandException("argument.range.empty");
        return new MTEntitySelector.WrappedRange(min, max);
    }

    private boolean shouldInvertValue() {
        skipWhitespace();
        if (canRead() && peek() == '!') {
            skip();
            skipWhitespace();
            return true;
        }
        return false;
    }

    private boolean isTag() {
        skipWhitespace();
        if (canRead() && peek() == '#') {
            skip();
            skipWhitespace();
            return true;
        }
        return false;
    }

    private String readLegacyValue() {
        int start = getCursor();
        while (canRead() && peek() != ',' && peek() != ']') skip();
        return this.input.substring(start, getCursor());
    }

    private String readCompound() {
        int start = getCursor();
        if (!canRead() || peek() != '{') {
            throw new CommandException("argument.entity.options.nbt.invalid", this.input.substring(start));
        }
        int depth = 0;
        char quote = 0;
        while (canRead()) {
            char character = read();
            if (quote != 0) {
                if (character == '\\') {
                    if (canRead()) read();
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '{' || character == '[') {
                depth++;
            } else if (character == '}' || character == ']') {
                depth--;
                if (depth == 0) return this.input.substring(start, getCursor());
            }
        }
        throw new CommandException("argument.entity.options.nbt.invalid", this.input.substring(start));
    }

    private void expect(char expected, String option) {
        if (!canRead() || peek() != expected) {
            throw new CommandException("argument.entity.options.valueless", option);
        }
        skip();
    }

    private static CommandException inapplicable(String option) {
        return new CommandException("argument.entity.options.inapplicable", option);
    }

    private static CommandException unsupported(String option) {
        return new CommandException("argument.entity.options.unsupported", option);
    }
}
