package org.cloudburstmc.protocol.bedrock.codec;

import com.nukkitx.math.vector.Vector2f;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NBTInputStream;
import com.nukkitx.nbt.NBTOutputStream;
import com.nukkitx.nbt.NbtUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraintData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraintType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.defintions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.defintions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.stackrequestactions.StackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureSettings;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.util.TriConsumer;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.cloudburstmc.protocol.common.util.Preconditions.checkArgument;
import static org.cloudburstmc.protocol.common.util.Preconditions.checkNotNull;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseBedrockCodecHelper implements BedrockCodecHelper {
    protected static final InternalLogger log = InternalLoggerFactory.getInstance(BaseBedrockCodecHelper.class);

    protected final TypeMap<EntityData> entityData;
    protected final TypeMap<EntityData.Type> entityDataTypes;
    protected final TypeMap<EntityFlag> entityFlags;
    protected final TypeMap<Class<?>> gameRuleType;

    @Setter
    protected DefinitionRegistry<ItemDefinition> itemDefinitions;
    @Setter
    protected DefinitionRegistry<BlockDefinition> blockDefinitions;

    protected static boolean isAir(ItemDefinition definition) {
        return definition == null || definition.getRuntimeId() == 0;
    }

    public byte[] readByteArray(ByteBuf buffer) {
        int length = VarInts.readUnsignedInt(buffer);
        checkArgument(buffer.isReadable(length),
                "Tried to read %s bytes but only has %s readable", length, buffer.readableBytes());
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    public void writeByteArray(ByteBuf buffer, byte[] bytes) {
        checkNotNull(bytes, "bytes");
        VarInts.writeUnsignedInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

    @Override
    public ByteBuf readByteBuf(ByteBuf buffer) {
        int length = VarInts.readUnsignedInt(buffer);
        return buffer.readRetainedSlice(length);
    }

    @Override
    public void writeByteBuf(ByteBuf buffer, ByteBuf toWrite) {
        checkNotNull(toWrite, "toWrite");
        VarInts.writeUnsignedInt(buffer, toWrite.readableBytes());
        buffer.writeBytes(toWrite, toWrite.readerIndex(), toWrite.writerIndex());
    }

    public String readString(ByteBuf buffer) {
        int length = VarInts.readUnsignedInt(buffer);
        return (String) buffer.readCharSequence(length, StandardCharsets.UTF_8);
    }

    public void writeString(ByteBuf buffer, String string) {
        checkNotNull(string, "string");
        VarInts.writeUnsignedInt(buffer, ByteBufUtil.utf8Bytes(string));
        buffer.writeCharSequence(string, StandardCharsets.UTF_8);
    }

    public UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLongLE(), buffer.readLongLE());
    }

    public void writeUuid(ByteBuf buffer, UUID uuid) {
        checkNotNull(uuid, "uuid");
        buffer.writeLongLE(uuid.getMostSignificantBits());
        buffer.writeLongLE(uuid.getLeastSignificantBits());
    }

    public Vector3f readVector3f(ByteBuf buffer) {
        float x = buffer.readFloatLE();
        float y = buffer.readFloatLE();
        float z = buffer.readFloatLE();
        return Vector3f.from(x, y, z);
    }

    public void writeVector3f(ByteBuf buffer, Vector3f vector3f) {
        checkNotNull(vector3f, "vector3f");
        buffer.writeFloatLE(vector3f.getX());
        buffer.writeFloatLE(vector3f.getY());
        buffer.writeFloatLE(vector3f.getZ());
    }

    public Vector2f readVector2f(ByteBuf buffer) {
        float x = buffer.readFloatLE();
        float y = buffer.readFloatLE();
        return Vector2f.from(x, y);
    }

    public void writeVector2f(ByteBuf buffer, Vector2f vector2f) {
        checkNotNull(vector2f, "vector2f");
        buffer.writeFloatLE(vector2f.getX());
        buffer.writeFloatLE(vector2f.getY());
    }


    public Vector3i readVector3i(ByteBuf buffer) {
        int x = VarInts.readInt(buffer);
        int y = VarInts.readInt(buffer);
        int z = VarInts.readInt(buffer);

        return Vector3i.from(x, y, z);
    }

    public void writeVector3i(ByteBuf buffer, Vector3i vector3i) {
        checkNotNull(vector3i, "vector3i");
        VarInts.writeInt(buffer, vector3i.getX());
        VarInts.writeInt(buffer, vector3i.getY());
        VarInts.writeInt(buffer, vector3i.getZ());
    }

    public Vector3i readBlockPosition(ByteBuf buffer) {
        int x = VarInts.readInt(buffer);
        int y = VarInts.readUnsignedInt(buffer);
        int z = VarInts.readInt(buffer);

        return Vector3i.from(x, y, z);
    }

    public void writeBlockPosition(ByteBuf buffer, Vector3i blockPosition) {
        checkNotNull(blockPosition, "blockPosition");
        VarInts.writeInt(buffer, blockPosition.getX());
        VarInts.writeUnsignedInt(buffer, blockPosition.getY());
        VarInts.writeInt(buffer, blockPosition.getZ());
    }

    /*
        Helper array serialization
     */

    @Override
    public <T> void readArray(ByteBuf buffer, Collection<T> array, BiFunction<ByteBuf, BedrockCodecHelper, T> function) {
        int length = VarInts.readUnsignedInt(buffer);
        for (int i = 0; i < length; i++) {
            array.add(function.apply(buffer, this));
        }
    }

    @Override
    public <T> void writeArray(ByteBuf buffer, Collection<T> array, TriConsumer<ByteBuf, BedrockCodecHelper, T> consumer) {
        VarInts.writeUnsignedInt(buffer, array.size());
        for (T val : array) {
            consumer.accept(buffer, this, val);
        }
    }

    @Override
    public <T> T[] readArray(ByteBuf buffer, T[] array, BiFunction<ByteBuf, BedrockCodecHelper, T> function) {
        ObjectArrayList<T> list = new ObjectArrayList<>();
        readArray(buffer, list, function);
        return list.toArray(array);
    }

    @Override
    public <T> void writeArray(ByteBuf buffer, T[] array, TriConsumer<ByteBuf, BedrockCodecHelper, T> consumer) {
        VarInts.writeUnsignedInt(buffer, array.length);
        for (T val : array) {
            consumer.accept(buffer, this, val);
        }
    }
    /*
        Non-helper array serialization
     */

    @Override
    public <T> void readArray(ByteBuf buffer, Collection<T> array, Function<ByteBuf, T> function) {
        int length = VarInts.readUnsignedInt(buffer);
        for (int i = 0; i < length; i++) {
            array.add(function.apply(buffer));
        }
    }

    @Override
    public <T> void writeArray(ByteBuf buffer, Collection<T> array, BiConsumer<ByteBuf, T> biConsumer) {
        VarInts.writeUnsignedInt(buffer, array.size());
        for (T val : array) {
            biConsumer.accept(buffer, val);
        }
    }

    @Override
    public <T> T[] readArray(ByteBuf buffer, T[] array, Function<ByteBuf, T> function) {
        ObjectArrayList<T> list = new ObjectArrayList<>();
        readArray(buffer, list, function);
        return list.toArray(array);
    }

    @Override
    public <T> void writeArray(ByteBuf buffer, T[] array, BiConsumer<ByteBuf, T> biConsumer) {
        VarInts.writeUnsignedInt(buffer, array.length);
        for (T val : array) {
            biConsumer.accept(buffer, val);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readTag(ByteBuf buffer, Class<T> expected) {
        try (NBTInputStream reader = NbtUtils.createNetworkReader(new ByteBufInputStream(buffer))) {
            Object tag = reader.readTag();
            checkArgument(expected.isInstance(tag), "Expected tag of %s type but received %s",
                    expected, tag.getClass());
            return (T) reader.readTag();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeTag(ByteBuf buffer, Object tag) {
        try (NBTOutputStream writer = NbtUtils.createNetworkWriter(new ByteBufOutputStream(buffer))) {
            writer.writeTag(tag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readTagLE(ByteBuf buffer, Class<T> expected) {
        try (NBTInputStream reader = NbtUtils.createReaderLE(new ByteBufInputStream(buffer))) {
            Object tag = reader.readTag();
            checkArgument(expected.isInstance(tag), "Expected tag of %s type but received %s",
                    expected, tag.getClass());
            return (T) reader.readTag();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeTagLE(ByteBuf buffer, Object tag) {
        try (NBTOutputStream writer = NbtUtils.createWriterLE(new ByteBufOutputStream(buffer))) {
            writer.writeTag(tag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readItemUse(ByteBuf buffer, InventoryTransactionPacket packet) {
        packet.setActionType(VarInts.readUnsignedInt(buffer));
        packet.setBlockPosition(this.readBlockPosition(buffer));
        packet.setBlockFace(VarInts.readInt(buffer));
        packet.setHotbarSlot(VarInts.readInt(buffer));
        packet.setItemInHand(this.readItem(buffer));
        packet.setPlayerPosition(this.readVector3f(buffer));
        packet.setClickPosition(this.readVector3f(buffer));
    }

    @Override
    public void writeItemUse(ByteBuf buffer, InventoryTransactionPacket packet) {
        VarInts.writeUnsignedInt(buffer, packet.getActionType());
        this.writeBlockPosition(buffer, packet.getBlockPosition());
        VarInts.writeInt(buffer, packet.getBlockFace());
        VarInts.writeInt(buffer, packet.getHotbarSlot());
        this.writeItem(buffer, packet.getItemInHand());
        this.writeVector3f(buffer, packet.getPlayerPosition());
        this.writeVector3f(buffer, packet.getClickPosition());
    }

    @Override
    public boolean readInventoryActions(ByteBuf buffer, List<InventoryActionData> actions) {
        this.readArray(buffer, actions, (buf, helper) -> {
            InventorySource source = this.readSource(buf);
            int slot = VarInts.readUnsignedInt(buf);
            ItemData fromItem = helper.readItem(buf);
            ItemData toItem = helper.readItem(buf);

            return new InventoryActionData(source, slot, fromItem, toItem);
        });
        return false;
    }

    @Override
    public void writeInventoryActions(ByteBuf buffer, List<InventoryActionData> actions, boolean hasNetworkIds) {
        this.writeArray(buffer, actions, (buf, helper, action) -> {
            this.writeSource(buf, action.getSource());
            VarInts.writeUnsignedInt(buf, action.getSlot());
            helper.writeItem(buf, action.getFromItem());
            helper.writeItem(buf, action.getToItem());
        });
    }

    protected InventorySource readSource(ByteBuf buffer) {
        InventorySource.Type type = InventorySource.Type.byId(VarInts.readUnsignedInt(buffer));

        switch (type) {
            case CONTAINER:
                int containerId = VarInts.readInt(buffer);
                return InventorySource.fromContainerWindowId(containerId);
            case GLOBAL:
                return InventorySource.fromGlobalInventory();
            case WORLD_INTERACTION:
                InventorySource.Flag flag = InventorySource.Flag.values()[VarInts.readUnsignedInt(buffer)];
                return InventorySource.fromWorldInteraction(flag);
            case CREATIVE:
                return InventorySource.fromCreativeInventory();
            case NON_IMPLEMENTED_TODO:
                containerId = VarInts.readInt(buffer);
                return InventorySource.fromNonImplementedTodo(containerId);
            default:
                return InventorySource.fromInvalid();
        }
    }

    protected void writeSource(ByteBuf buffer, InventorySource inventorySource) {
        requireNonNull(inventorySource, "InventorySource was null");

        VarInts.writeUnsignedInt(buffer, inventorySource.getType().id());

        switch (inventorySource.getType()) {
            case CONTAINER:
            case UNTRACKED_INTERACTION_UI:
            case NON_IMPLEMENTED_TODO:
                VarInts.writeInt(buffer, inventorySource.getContainerId());
                break;
            case WORLD_INTERACTION:
                VarInts.writeUnsignedInt(buffer, inventorySource.getFlag().ordinal());
                break;
        }
    }

    public ItemData readRecipeIngredient(ByteBuf buffer) {
        int runtimeId = VarInts.readInt(buffer);
        if (runtimeId == 0) {
            // We don't need to read anything extra.
            return ItemData.AIR;
        }
        ItemDefinition definition = this.itemDefinitions.getDefinition(runtimeId);

        int meta = VarInts.readInt(buffer);
        int count = VarInts.readInt(buffer);

        return ItemData.builder()
                .definition(definition)
                .damage(meta)
                .count(count)
                .build();
    }

    public void writeRecipeIngredient(ByteBuf buffer, ItemData item) {
        requireNonNull(item, "item is null");

        VarInts.writeInt(buffer, item.getDefinition().getRuntimeId());

        if (isAir(item.getDefinition())) {
            return;
        }

        VarInts.writeInt(buffer, item.getDamage());
        VarInts.writeInt(buffer, item.getCount());
    }

    public PotionMixData readPotionRecipe(ByteBuf buffer) {

        return new PotionMixData(
                VarInts.readInt(buffer),
                VarInts.readInt(buffer),
                VarInts.readInt(buffer),
                VarInts.readInt(buffer),
                VarInts.readInt(buffer),
                VarInts.readInt(buffer)
        );
    }

    public void writePotionRecipe(ByteBuf buffer, PotionMixData data) {
        requireNonNull(data, "data is null");

        VarInts.writeInt(buffer, data.getInputId());
        VarInts.writeInt(buffer, data.getInputMeta());
        VarInts.writeInt(buffer, data.getReagentId());
        VarInts.writeInt(buffer, data.getReagentMeta());
        VarInts.writeInt(buffer, data.getOutputId());
        VarInts.writeInt(buffer, data.getOutputMeta());
    }

    public ContainerMixData readContainerChangeRecipe(ByteBuf buffer) {

        return new ContainerMixData(
                VarInts.readInt(buffer),
                VarInts.readInt(buffer),
                VarInts.readInt(buffer)
        );
    }

    public void writeContainerChangeRecipe(ByteBuf buffer, ContainerMixData data) {
        requireNonNull(data, "data is null");

        VarInts.writeInt(buffer, data.getInputId());
        VarInts.writeInt(buffer, data.getReagentId());
        VarInts.writeInt(buffer, data.getOutputId());
    }

    public CommandEnumConstraintData readCommandEnumConstraints(ByteBuf buffer, List<CommandEnumData> enums, List<String> enumValues) {
        int valueIndex = buffer.readIntLE();
        int enumIndex = buffer.readIntLE();
        CommandEnumConstraintType[] constraints = readArray(buffer, new CommandEnumConstraintType[0],
                buf -> CommandEnumConstraintType.byId(buffer.readByte()));

        return new CommandEnumConstraintData(
                enumValues.get(valueIndex),
                enums.get(enumIndex),
                constraints
        );
    }

    public void writeCommandEnumConstraints(ByteBuf buffer, CommandEnumConstraintData data, List<CommandEnumData> enums, List<String> enumValues) {
        buffer.writeIntLE(enumValues.indexOf(data.getOption()));
        buffer.writeIntLE(enums.indexOf(data.getEnumData()));
        writeArray(buffer, data.getConstraints(), (buf, constraint) -> {
            buf.writeByte(constraint.ordinal());
        });
    }

    public void readExperiments(ByteBuf buffer, List<ExperimentData> experiments) {
        throw new UnsupportedOperationException();
    }

    public void writeExperiments(ByteBuf buffer, List<ExperimentData> experiments) {
        throw new UnsupportedOperationException();
    }

    protected void registerStackActionRequestTypes() {
        throw new UnsupportedOperationException();
    }

    public StackRequestActionType getStackRequestActionTypeFromId(int id) {
        throw new UnsupportedOperationException();
    }

    public int getIdFromStackRequestActionType(StackRequestActionType type) {
        throw new UnsupportedOperationException();
    }

    public ItemStackRequest readItemStackRequest(ByteBuf buffer) {
        throw new UnsupportedOperationException();
    }

    public void writeItemStackRequest(ByteBuf buffer, ItemStackRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StructureSettings readStructureSettings(ByteBuf buffer) {
        return null;
    }

    @Override
    public void writeStructureSettings(ByteBuf buffer, StructureSettings settings) {

    }

    @Override
    public SerializedSkin readSkin(ByteBuf buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeSkin(ByteBuf buffer, SerializedSkin skin) {
        throw new UnsupportedOperationException();
    }

    // Internal methods

    public AnimationData readAnimationData(ByteBuf buffer) {
        throw new UnsupportedOperationException();
    }

    protected void writeAnimationData(ByteBuf buffer, AnimationData animation) {
        throw new UnsupportedOperationException();
    }

    protected ImageData readImage(ByteBuf buffer) {
        throw new UnsupportedOperationException();
    }

    protected void writeImage(ByteBuf buffer, ImageData image) {
        throw new UnsupportedOperationException();
    }
}
