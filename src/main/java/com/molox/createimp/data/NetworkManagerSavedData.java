package com.molox.createimp.data;

import com.molox.createimp.item.NetworkLabel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public class NetworkManagerSavedData extends SavedData {

    private static final String DATA_KEY = "createimp_network_manager";
    private static final String TAG_LABELS = "Labels";

    private final List<NetworkLabel> labels = new ArrayList<>();

    public static SavedData.Factory<NetworkManagerSavedData> factory(HolderLookup.Provider registries) {
        return new SavedData.Factory<>(
                NetworkManagerSavedData::new,
                (tag, regs) -> load(tag, regs),
                null
        );
    }

    public static NetworkManagerSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(factory(server.registryAccess()), DATA_KEY);
    }

    private static NetworkManagerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkManagerSavedData data = new NetworkManagerSavedData();
        ListTag listTag = tag.getList(TAG_LABELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            NetworkLabel.CODEC.parse(NbtOps.INSTANCE, entry)
                    .resultOrPartial(err -> {})
                    .ifPresent(data.labels::add);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();
        for (NetworkLabel label : labels) {
            NetworkLabel.CODEC.encodeStart(NbtOps.INSTANCE, label)
                    .resultOrPartial(err -> {})
                    .ifPresent(t -> listTag.add(t));
        }
        tag.put(TAG_LABELS, listTag);
        return tag;
    }

    public List<NetworkLabel> getLabels() {
        return labels;
    }

    public void setLabels(List<NetworkLabel> newLabels) {
        labels.clear();
        labels.addAll(newLabels);
        setDirty();
    }
}