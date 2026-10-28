"""Builds the Ranger HQ village house as structure templates, one per village type.

Run from the repository root: python3 tools/hq_structure.py

The house is a flat-roofed post with a red Ranger stripe, a Ranger HQ terminal on the back wall, a supply chest and a
Ranger villager. The entrance jigsaw follows the vanilla village houses: it sits on the west side at floor level and
joins the street's "minecraft:building_entrance" jigsaw. RangerHq.addToVillages adds the templates to the village
house pools when the server starts.
"""
import os

import nbt

OUT = "src/main/resources/data/cobblemonranger/structure/village"
DATA_VERSION = 3955  # 1.21.1

VILLAGES = {
    "plains": dict(planks="oak_planks", corner="stripped_oak_log[axis=y]", slab="oak_slab[type=bottom]",
                   stairs="oak_stairs", base="cobblestone", door="oak_door", villager="plains"),
    "desert": dict(planks="smooth_sandstone", corner="cut_sandstone", slab="smooth_sandstone_slab[type=bottom]",
                   stairs="sandstone_stairs", base="sandstone", door="jungle_door", villager="desert"),
    "savanna": dict(planks="acacia_planks", corner="stripped_acacia_log[axis=y]", slab="acacia_slab[type=bottom]",
                    stairs="acacia_stairs", base="cobblestone", door="acacia_door", villager="savanna"),
    "snowy": dict(planks="spruce_planks", corner="stripped_spruce_log[axis=y]", slab="spruce_slab[type=bottom]",
                  stairs="spruce_stairs", base="stone_bricks", door="spruce_door", villager="snow"),
    "taiga": dict(planks="spruce_planks", corner="spruce_log[axis=y]", slab="spruce_slab[type=bottom]",
                  stairs="spruce_stairs", base="mossy_cobblestone", door="spruce_door", villager="taiga"),
}

# x runs from the street (x = 0, the entrance step) to the back wall (x = 8); z is across the front, door at z = 4.
SIZE = (9, 7, 9)
FRONT, BACK, LEFT, RIGHT = 1, 8, 0, 8
DOOR_Z = 4


def parse(state):
    name, _, props = state.partition("[")
    name = name if ":" in name else "minecraft:" + name
    properties = {}
    if props:
        for pair in props.rstrip("]").split(","):
            key, _, value = pair.partition("=")
            properties[key] = value
    return name, properties


def build(p):
    blocks = {}  # (x, y, z) -> (state, block entity nbt or None)

    def put(x, y, z, state, data=None):
        blocks[(x, y, z)] = (state, data)

    for x in range(SIZE[0]):
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                put(x, y, z, "air")

    wall_x = range(FRONT, BACK + 1)
    wall_z = range(LEFT, RIGHT + 1)
    for x in wall_x:
        for z in wall_z:
            edge_x, edge_z = x in (FRONT, BACK), z in (LEFT, RIGHT)
            # Floor: foundation ring, planks inside.
            put(x, 0, z, p["base"] if edge_x or edge_z else p["planks"])
            if edge_x or edge_z:
                for y in (1, 2, 3):
                    if edge_x and edge_z:
                        state = p["corner"]
                    elif y == 3:
                        state = "red_terracotta"  # the Ranger stripe
                    else:
                        state = p["planks"]
                    put(x, y, z, state)
            # Flat roof with a red parapet.
            put(x, 4, z, p["slab"] if not (edge_x or edge_z) else p["planks"])
            if edge_x or edge_z:
                put(x, 5, z, "red_terracotta" if (x + z) % 2 == 0 else "air")

    # Windows.
    for z in (2, 6):
        put(FRONT, 2, z, "glass_pane[east=false,north=true,south=true,west=false]")
        put(BACK, 2, z, "glass_pane[east=false,north=true,south=true,west=false]")
    for x in (3, 6):
        put(x, 2, LEFT, "glass_pane[east=true,north=false,south=false,west=true]")
        put(x, 2, RIGHT, "glass_pane[east=true,north=false,south=false,west=true]")

    # Door and the step outside it, which is where the entrance jigsaw sits.
    door = "%s[facing=east,half=%s,hinge=left,open=false,powered=false]"
    put(FRONT, 1, DOOR_Z, door % (p["door"], "lower"))
    put(FRONT, 2, DOOR_Z, door % (p["door"], "upper"))
    step = "minecraft:%s[facing=east,half=bottom,shape=straight,waterlogged=false]" % p["stairs"]
    put(0, 0, DOOR_Z, "jigsaw[orientation=west_up]", {
        "id": "minecraft:jigsaw",
        "name": "minecraft:building_entrance",
        "target": "minecraft:building_entrance",
        "pool": "minecraft:empty",
        "joint": "aligned",
        "final_state": step,
    })
    # Inside: red carpet from the door to the terminal, which faces the door.
    for x in range(FRONT + 1, BACK - 1):
        put(x, 1, DOOR_Z, "red_carpet")
    put(BACK - 1, 1, DOOR_Z, "cobblemonranger:ranger_hq[facing=west]")
    put(BACK - 1, 1, DOOR_Z - 2, "chest[facing=west,type=single,waterlogged=false]",
        {"id": "minecraft:chest", "LootTable": "cobblemonranger:chests/ranger_hq"})
    put(BACK - 1, 1, DOOR_Z + 2, "bookshelf")
    put(BACK - 1, 2, DOOR_Z + 2, "bookshelf")
    put(BACK - 1, 1, DOOR_Z + 3, "crafting_table")
    put(FRONT + 1, 1, LEFT + 1, "potted_poppy")
    put(FRONT + 1, 1, RIGHT - 1, "potted_dandelion")
    put(4, 3, DOOR_Z, "lantern[hanging=true,waterlogged=false]")
    put(BACK - 1, 3, DOOR_Z, "lantern[hanging=true,waterlogged=false]")
    # A red banner flying on the roof above the door.
    put(FRONT + 1, 5, DOOR_Z, "red_banner[rotation=4]")

    palette, palette_index, block_list = [], {}, []
    for (x, y, z), (state, data) in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        name, props = parse(state)
        key = (name, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            palette.append(entry)
        block = {"pos": nbt.List(nbt.INT, [nbt.Int(x), nbt.Int(y), nbt.Int(z)]),
                 "state": nbt.Int(palette_index[key])}
        if data is not None:
            block["nbt"] = data
        block_list.append(block)

    villager_pos = (BACK - 2.5, 1.0, DOOR_Z + 0.5)
    villager = {
        "pos": nbt.List(nbt.DOUBLE, [nbt.Double(v) for v in villager_pos]),
        "blockPos": nbt.List(nbt.INT, [nbt.Int(int(v)) for v in villager_pos]),
        "nbt": {
            "id": "minecraft:villager",
            "VillagerData": {"profession": "cobblemonranger:ranger", "level": nbt.Int(1),
                             "type": "minecraft:" + p["villager"]},
            "Rotation": nbt.List(nbt.FLOAT, [nbt.Float(90.0), nbt.Float(0.0)]),
        },
    }

    return {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.List(nbt.INT, [nbt.Int(v) for v in SIZE]),
        "palette": nbt.List(nbt.COMPOUND, palette),
        "blocks": nbt.List(nbt.COMPOUND, block_list),
        "entities": nbt.List(nbt.COMPOUND, [villager]),
    }


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for village, palette in VILLAGES.items():
        nbt.save(os.path.join(OUT, "%s_ranger_hq.nbt" % village), build(palette))
