from rcon import rcon
Y=-60
future=[('white_wool_stairs[facing=north]','quartz_stairs[facing=north]'),
        ('magenta_wool_stairs[facing=north]','purpur_stairs[facing=north]'),
        ('red_concrete_slab','brick_slab'),
        ('lime_wool_slab','prismarine_slab'),
        ('poplar_log','birch_log'),
        ('poplar_planks','birch_planks'),
        ('sulfur','sandstone'),
        ('sulfur_bricks','end_stone_bricks'),
        ('cinnabar','granite'),
        ('cinnabar_bricks','bricks'),
        ('red_poplar_leaves[persistent=true]','dark_oak_leaves[persistent=true]'),
        ('golden_dandelion','dandelion'),
        ('potted_poplar_sapling','potted_birch_sapling'),
        ('sulfur_wall','sandstone_wall')]
cmds=['fill -10 -60 -10 10 -50 10 air']
for i,(f,r) in enumerate(future):
    x=-7+i
    cmds.append(f'setblock {x} {Y} 0 minecraft:{f}')
    cmds.append(f'setblock {x} {Y} 2 minecraft:{r}')
cmds.append('setblock 0 -60 4 minecraft:poplar_door[half=lower,facing=north]')
cmds.append('setblock 0 -59 4 minecraft:poplar_door[half=upper,facing=north]')
cmds.append('setblock 2 -60 4 minecraft:poplar_trapdoor[facing=north,half=bottom,open=false]')
cmds.append('setblock -2 -60 4 minecraft:sulfur_spike[vertical_direction=up,thickness=tip]')
cmds.append('setblock -3 -63 7 minecraft:potent_sulfur[potent_sulfur_state=erupting]')
cmds.append('setblock -3 -62 7 minecraft:water')
cmds.append('setblock -3 -61 7 minecraft:water')
cmds.append('summon minecraft:cushion 4.5 -60 4.5 {color:"red"}')
cmds.append('summon minecraft:cushion 5.5 -60 4.5 {color:"light_blue"}')
for i,b in enumerate(['cow','pig','sheep','fox','wolf','cat','chicken','panda']):
    cmds.append(f'summon minecraft:{b} {-6+i*1.5} -60 -2 {{NoAI:1b,Silent:1b,Age:-2147483648,Rotation:[180f,0f]}}')
cmds.append('summon minecraft:sulfur_cube 6.5 -60 -2.5 {NoAI:1b,Silent:1b}')
for c,r in zip(cmds, rcon(cmds)):
    if r.strip() and 'Changed the block' not in r and 'Summoned' not in r: print(c,'->',r)
print('scene ok')
