document.addEventListener('DOMContentLoaded', () => {
    console.log('页面加载完成，初始化应用...');
    
    const playerList = document.getElementById('playerList');
    const inventorySlots = document.getElementById('inventorySlots');
    const serverSelectorHeader = document.getElementById('serverSelectorHeader');
    const serverList = document.getElementById('serverList');
    
    // 服务器选择相关变量
    let currentServer = null;
    let servers = [];
    
    // 初始状态显示加载中
    playerList.innerHTML = '<div class="notice">正在加载服务器列表，请稍候...</div>';
    
    // 控制自动刷新的变量
    let lastServerListUpdate = 0;
    let lastPlayerListUpdate = 0;
    const SERVER_LIST_UPDATE_INTERVAL = 10000; // 减少为10秒，原来是30秒
    const PLAYER_LIST_UPDATE_INTERVAL = 5000; // 减少为5秒，原来是10秒
    
    // 用户交互状态标志
    let isUserInteracting = false;   // 用户是否正在与界面交互
    let isViewingPlayerDetails = false;  // 用户是否正在查看玩家详情
    let userInteractionTimeout = null;   // 用户交互状态重置定时器
    
    // 初始化应用
    // 立即更新服务器列表
    updateServerList();
    
    // 设置一个单一的定时器，管理所有更新
    setInterval(() => {
        const now = Date.now();
        
        // 如果用户正在查看玩家详情，暂停自动更新
        if (isViewingPlayerDetails) {
            return;
        }
        
        // 检查是否需要更新服务器列表
        if (now - lastServerListUpdate >= SERVER_LIST_UPDATE_INTERVAL) {
            updateServerList();
            lastServerListUpdate = now;
        }
        
        // 检查是否需要更新玩家列表
        if (now - lastPlayerListUpdate >= PLAYER_LIST_UPDATE_INTERVAL) {
            updatePlayerList(currentServer);
            lastPlayerListUpdate = now;
        }
    }, 5000); // 每5秒检查一次是否需要更新
    
    // 监听用户交互，标记交互状态
    function startUserInteraction() {
        isUserInteracting = true;
        
        // 清除之前的定时器
        if (userInteractionTimeout) {
            clearTimeout(userInteractionTimeout);
        }
        
        // 设置新的定时器，3秒后恢复自动刷新
        userInteractionTimeout = setTimeout(() => {
            isUserInteracting = false;
        }, 3000);
    }
    
    // 服务器选择器弹出事件
    serverSelectorHeader.addEventListener('click', () => {
        startUserInteraction();
        
        // 检查是否有服务器可选
        if (servers.length === 0) {
            // 更新服务器列表
            updateServerList();
        }
        
        serverList.classList.toggle('active');
        serverSelectorHeader.querySelector('.arrow-icon').classList.toggle('active');
    });
    
    // 监听滚动事件
    document.addEventListener('scroll', startUserInteraction);
    
    // 监听触摸事件
    document.addEventListener('touchstart', startUserInteraction);
    document.addEventListener('touchmove', startUserInteraction);
    
    // Minecraft color codes to CSS colors
    const colorCodes = {
        '0': '#000000', // Black
        '1': '#0000AA', // Dark Blue
        '2': '#00AA00', // Dark Green
        '3': '#00AAAA', // Dark Aqua
        '4': '#AA0000', // Dark Red
        '5': '#AA00AA', // Dark Purple
        '6': '#FFAA00', // Gold
        '7': '#AAAAAA', // Gray
        '8': '#555555', // Dark Gray
        '9': '#5555FF', // Blue
        'a': '#55FF55', // Green
        'b': '#55FFFF', // Aqua
        'c': '#FF5555', // Red
        'd': '#FF55FF', // Light Purple
        'e': '#FFFF55', // Yellow
        'f': '#FFFFFF', // White
    };
    
    // 物品图标URL基础路径
    const ITEM_ICONS_BASE_PATH = './img/items/';
    const BLOCK_ICONS_BASE_PATH = './img/blocks/';
    
    // 物品ID到图标文件名的映射
    const itemIconMapping = {
        // 工具和武器
        'WOODEN_SWORD': 'wood_sword.png',
        'STONE_SWORD': 'stone_sword.png',
        'IRON_SWORD': 'iron_sword.png',
        'GOLDEN_SWORD': 'gold_sword.png',
        'DIAMOND_SWORD': 'diamond_sword.png',
        'WOODEN_PICKAXE': 'wood_pickaxe.png',
        'STONE_PICKAXE': 'stone_pickaxe.png',
        'IRON_PICKAXE': 'iron_pickaxe.png',
        'GOLDEN_PICKAXE': 'gold_pickaxe.png',
        'GOLD_PICKAXE': 'gold_pickaxe.png',
        'DIAMOND_PICKAXE': 'diamond_pickaxe.png',
        'WOODEN_AXE': 'wood_axe.png',
        'STONE_AXE': 'stone_axe.png',
        'IRON_AXE': 'iron_axe.png',
        'GOLDEN_AXE': 'gold_axe.png',
        'DIAMOND_AXE': 'diamond_axe.png',
        'WOODEN_SHOVEL': 'wood_shovel.png',
        'STONE_SHOVEL': 'stone_shovel.png',
        'IRON_SHOVEL': 'iron_shovel.png',
        'GOLDEN_SHOVEL': 'gold_shovel.png',
        'DIAMOND_SHOVEL': 'diamond_shovel.png',
        'WOODEN_HOE': 'wood_hoe.png',
        'STONE_HOE': 'stone_hoe.png',
        'IRON_HOE': 'iron_hoe.png',
        'GOLDEN_HOE': 'gold_hoe.png',
        'DIAMOND_HOE': 'diamond_hoe.png',
        'BOW': 'bow_standby.png',
        'FISHING_ROD': 'fishing_rod_uncast.png',
        'SHEARS': 'shears.png',
        'FLINT_AND_STEEL': 'flint_and_steel.png',
        
        // 盔甲
        'LEATHER_HELMET': 'leather_helmet.png',
        'LEATHER_CHESTPLATE': 'leather_chestplate.png',
        'LEATHER_LEGGINGS': 'leather_leggings.png',
        'LEATHER_BOOTS': 'leather_boots.png',
        'CHAINMAIL_HELMET': 'chainmail_helmet.png',
        'CHAINMAIL_CHESTPLATE': 'chainmail_chestplate.png',
        'CHAINMAIL_LEGGINGS': 'chainmail_leggings.png',
        'CHAINMAIL_BOOTS': 'chainmail_boots.png',
        'IRON_HELMET': 'iron_helmet.png',
        'IRON_CHESTPLATE': 'iron_chestplate.png',
        'IRON_LEGGINGS': 'iron_leggings.png',
        'IRON_BOOTS': 'iron_boots.png',
        'GOLDEN_HELMET': 'gold_helmet.png',
        'GOLDEN_CHESTPLATE': 'gold_chestplate.png',
        'GOLDEN_LEGGINGS': 'gold_leggings.png',
        'GOLDEN_BOOTS': 'gold_boots.png',
        'GOLD_HELMET': 'gold_helmet.png',
        'GOLD_CHESTPLATE': 'gold_chestplate.png',
        'GOLD_LEGGINGS': 'gold_leggings.png',
        'GOLD_BOOTS': 'gold_boots.png',
        'DIAMOND_HELMET': 'diamond_helmet.png',
        'DIAMOND_CHESTPLATE': 'diamond_chestplate.png',
        'DIAMOND_LEGGINGS': 'diamond_leggings.png',
        'DIAMOND_BOOTS': 'diamond_boots.png',
        'ELYTRA': 'elytra.png',
        'SHIELD': 'shield.png',
        
        // 食物和消耗品
        'APPLE': 'apple.png',
        'GOLDEN_APPLE': 'apple_golden.png',
        'BREAD': 'bread.png',
        'PORKCHOP': 'porkchop_raw.png',
        'COOKED_PORKCHOP': 'porkchop_cooked.png',
        'GRILLED_PORK': 'porkchop_cooked.png',
        'COOKIE': 'cookie.png',
        'MELON': 'melon.png',
        'BEEF': 'beef_raw.png',
        'COOKED_BEEF': 'beef_cooked.png',
        'RAW_BEEF': 'beef_raw.png',
        'CHICKEN': 'chicken_raw.png',
        'COOKED_CHICKEN': 'chicken_cooked.png',
        'RAW_CHICKEN': 'chicken_raw.png',
        'CARROT': 'carrot.png',
        'POTATO': 'potato.png',
        'POTATO_ITEM': 'potato.png',
        'BAKED_POTATO': 'potato_baked.png',
        'MUSHROOM_STEW': 'mushroom_stew.png',
        'CAKE': 'cake.png',
        'PUMPKIN_PIE': 'pumpkin_pie.png',
        'ROTTEN_FLESH': 'rotten_flesh.png',
        'CHORUS_FRUIT': 'chorus_fruit.png',
        'CHORUS_FRUIT_POPPED': 'chorus_fruit_popped.png',
        'POISONOUS_POTATO': 'potato_poisonous.png',
        'BEETROOT': 'beetroot.png',
        'BEETROOT_SOUP': 'beetroot_soup.png',
        'RABBIT_STEW': 'rabbit_stew.png',
        'SPIDER_EYE': 'spider_eye.png',
        'FERMENTED_SPIDER_EYE': 'spider_eye_fermented.png',
        'COD': 'fish_cod_raw.png',
        'COOKED_COD': 'fish_cod_cooked.png',
        'RAW_FISH': 'fish_cod_raw.png',
        'COOKED_FISH': 'fish_cod_cooked.png',
        'SALMON': 'fish_salmon_raw.png',
        'COOKED_SALMON': 'fish_salmon_cooked.png',
        'TROPICAL_FISH': 'fish_clownfish_raw.png',
        'PUFFERFISH': 'fish_pufferfish_raw.png',
        'RABBIT': 'rabbit_raw.png',
        'COOKED_RABBIT': 'rabbit_cooked.png',
        'MUTTON': 'mutton_raw.png',
        'COOKED_MUTTON': 'mutton_cooked.png',
        'BOWL': 'bowl.png',
        'MILK_BUCKET': 'milk_bucket.png',
        'WATER_BUCKET': 'water_bucket.png',
        'LAVA_BUCKET': 'lava_bucket.png',
        
        // 重要物品和材料
        'ARROW': 'arrow.png',
        'COAL': 'coal.png',
        'DIAMOND': 'diamond.png',
        'IRON_INGOT': 'iron_ingot.png',
        'GOLD_INGOT': 'gold_ingot.png',
        'EMERALD': 'emerald.png',
        'STICK': 'stick.png',
        'STRING': 'string.png',
        'FEATHER': 'feather.png',
        'GUNPOWDER': 'gunpowder.png',
        'SULPHUR': 'gunpowder.png',
        'WHEAT': 'wheat.png',
        'WHEAT_SEEDS': 'wheat_seeds.png',
        'FLINT': 'flint.png',
        'REDSTONE': 'redstone_dust.png',
        'GLOWSTONE_DUST': 'glowstone_dust.png',
        'EGG': 'egg.png',
        'LAPIS_LAZULI': 'dye_powder_blue.png',
        'COMPASS': 'compass_16.png',
        'CLOCK': 'clock_16.png',
        'SUGAR': 'sugar.png',
        'ENDER_PEARL': 'ender_pearl.png',
        'CLAY_BALL': 'clay_ball.png',
        'LEATHER': 'leather.png',
        'BLAZE_ROD': 'blaze_rod.png',
        'BLAZE_POWDER': 'blaze_powder.png',
        'MAGMA_CREAM': 'magma_cream.png',
        'BREWING_STAND': 'brewing_stand.png',
        'CAULDRON': 'cauldron.png',
        'BOOK': 'book_normal.png',
        'ENCHANTED_BOOK': 'book_enchanted.png',
        'WRITTEN_BOOK': 'book_written.png',
        'PAPER': 'paper.png',
        'QUARTZ': 'quartz.png',
        'NETHER_BRICK': 'netherbrick.png',
        'NETHER_STAR': 'nether_star.png',
        'SADDLE': 'saddle.png',
        'IRON_HORSE_ARMOR': 'iron_horse_armor.png',
        'GOLDEN_HORSE_ARMOR': 'gold_horse_armor.png',
        'DIAMOND_HORSE_ARMOR': 'diamond_horse_armor.png',
        'BUCKET': 'bucket_empty.png',
        'MINECART': 'minecart_normal.png',
        'OAK_BOAT': 'boat_oak.png',
        'WOODEN_DOOR': 'door_wood.png',
        'IRON_DOOR': 'door_iron.png',
        'EXPERIENCE_BOTTLE': 'experience_bottle.png',
        'ENDER_EYE': 'ender_eye.png',
        'GHAST_TEAR': 'ghast_tear.png',
        'NETHER_WART': 'nether_wart.png',
        
        // 药水和特殊物品
        'POTION': 'potion_bottle_drinkable.png',
        'SPLASH_POTION': 'potion_bottle_splash.png',
        'LINGERING_POTION': 'potion_bottle_lingering.png',
        'DRAGON_BREATH': 'dragon_breath.png',
        'DRAGONS_BREATH': 'dragon_breath.png',
        'SPAWN_EGG': 'spawn_egg.png',
        'FIREWORK_ROCKET': 'fireworks.png',
        'FIREWORK_STAR': 'fireworks_charge.png',
        'FIREWORK_CHARGE': 'fireworks_charge.png',
        'FIREWORK': 'fireworks.png',
        'MAP': 'map_empty.png',
        'FILLED_MAP': 'map_filled.png',
        'BANNER': 'banner_overlay.png',
        'SIGN': 'sign.png',
        'NAME_TAG': 'name_tag.png',
        'MUSIC_DISC_11': 'record_11.png',
        'MUSIC_DISC_13': 'record_13.png',
        'MUSIC_DISC_BLOCKS': 'record_blocks.png',
        'MUSIC_DISC_CAT': 'record_cat.png',
        'MUSIC_DISC_CHIRP': 'record_chirp.png',
        'MUSIC_DISC_FAR': 'record_far.png',
        'MUSIC_DISC_MALL': 'record_mall.png',
        'MUSIC_DISC_MELLOHI': 'record_mellohi.png',
        'MUSIC_DISC_STAL': 'record_stal.png',
        'MUSIC_DISC_STRAD': 'record_strad.png',
        'MUSIC_DISC_WAIT': 'record_wait.png',
        'MUSIC_DISC_WARD': 'record_ward.png',
        'SKELETON_SKULL': 'skull_skeleton.png',
        'WITHER_SKELETON_SKULL': 'skull_wither.png',
        'ZOMBIE_HEAD': 'skull_zombie.png',
        'PLAYER_HEAD': 'skull_char.png',
        'CREEPER_HEAD': 'skull_creeper.png',
        'LEAD': 'lead.png',
        'SLIME_BALL': 'slimeball.png',
        
        // 染料
        'BLACK_DYE': 'dye_powder_black.png',
        'BLUE_DYE': 'dye_powder_blue.png',
        'BROWN_DYE': 'dye_powder_brown.png',
        'CYAN_DYE': 'dye_powder_cyan.png',
        'GRAY_DYE': 'dye_powder_gray.png',
        'GREEN_DYE': 'dye_powder_green.png',
        'LIGHT_BLUE_DYE': 'dye_powder_light_blue.png',
        'LIME_DYE': 'dye_powder_lime.png',
        'MAGENTA_DYE': 'dye_powder_magenta.png',
        'ORANGE_DYE': 'dye_powder_orange.png',
        'PINK_DYE': 'dye_powder_pink.png',
        'PURPLE_DYE': 'dye_powder_purple.png',
        'RED_DYE': 'dye_powder_red.png',
        'LIGHT_GRAY_DYE': 'dye_powder_silver.png',
        'WHITE_DYE': 'dye_powder_white.png',
        'YELLOW_DYE': 'dye_powder_yellow.png',
        
        // 工具与红石元件
        'RAIL': 'rail_normal.png',
        'ACTIVATOR_RAIL': 'rail_activator.png',
        'DETECTOR_RAIL': 'rail_detector.png',
        'POWERED_RAIL': 'rail_golden.png',
        'COMPARATOR': 'comparator.png',
        'REPEATER': 'repeater.png',
        'TNT': 'tnt.png',
        'HOPPER': 'hopper.png',
        'DISPENSER': 'dispenser.png',
        'DROPPER': 'dropper.png',
        'PISTON': 'piston.png',
        'REDSTONE_LAMP': 'redstone_lamp_off.png',
        'TRIPWIRE_HOOK': 'tripwire_hook.png',
        'LEVER': 'lever.png',
        'STONE_BUTTON': 'button.png',
        'WOODEN_BUTTON': 'button.png',
        'DAYLIGHT_DETECTOR': 'daylight_detector.png',
        'CHEST': 'chest.png',
        'TRAPPED_CHEST': 'trapped_chest.png',
        'ENCHANTING_TABLE': 'enchanting_table.png',
        'ANVIL': 'anvil.png',
        'END_CRYSTAL': 'end_crystal.png',
        'DRAGON_EGG': 'dragon_egg.png',
        'COMMAND_BLOCK': 'command_block.png',
        'STRUCTURE_BLOCK': 'structure_block.png',
        
        // 染料和特殊物品
        'INK_SACK': 'dye_powder_black.png',
        'INK_SACK:0': 'dye_powder_black.png',
        'INK_SACK:1': 'dye_powder_red.png',
        'INK_SACK:2': 'dye_powder_green.png',
        'INK_SACK:3': 'dye_powder_brown.png',
        'INK_SACK:4': 'dye_powder_blue.png',
        'INK_SACK:5': 'dye_powder_purple.png',
        'INK_SACK:6': 'dye_powder_cyan.png',
        'INK_SACK:7': 'dye_powder_silver.png',
        'INK_SACK:8': 'dye_powder_gray.png',
        'INK_SACK:9': 'dye_powder_pink.png',
        'INK_SACK:10': 'dye_powder_lime.png',
        'INK_SACK:11': 'dye_powder_yellow.png',
        'INK_SACK:12': 'dye_powder_light_blue.png',
        'INK_SACK:13': 'dye_powder_magenta.png',
        'INK_SACK:14': 'dye_powder_orange.png',
        'INK_SACK:15': 'dye_powder_white.png',
        'PRISMARINE_CRYSTALS': 'prismarine_crystals.png',
        'PRISMARINE_SHARD': 'prismarine_shard.png',
        'WATCH': 'clock_16.png',
        'NETHER_BRICK_ITEM': 'netherbrick.png',
        'TORCH': '../blocks/redstone_torch_on.png',
        'CLAY_BRICK': '../blocks/brick.png',
        'SKULL_ITEM': 'skull_skeleton.png',
        'SKULL_ITEM:0': 'skull_skeleton.png',
        'SKULL_ITEM:1': 'skull_wither.png',
        'SKULL_ITEM:2': 'skull_zombie.png',
        'SKULL_ITEM:3': 'skull_char.png',
        'SKULL_ITEM:4': 'skull_creeper.png',
    };
    
    // 方块类物品映射到方块材质
    const blockMapping = {
        'STONE': 'stone.png',
        'GRASS': 'grass_top.png',
        'GRASS_BLOCK': 'grass_top.png',
        'DIRT': 'dirt.png',
        'COBBLESTONE': 'cobblestone.png',
        'WOOD': 'planks_oak.png',
        'OAK_PLANKS': 'planks_oak.png',
        'SPRUCE_PLANKS': 'planks_spruce.png',
        'BIRCH_PLANKS': 'planks_birch.png',
        'JUNGLE_PLANKS': 'planks_jungle.png',
        'ACACIA_PLANKS': 'planks_acacia.png',
        'DARK_OAK_PLANKS': 'planks_big_oak.png',
        'BEDROCK': 'bedrock.png',
        'SAND': 'sand.png',
        'GRAVEL': 'gravel.png',
        'GOLD_ORE': 'gold_ore.png',
        'IRON_ORE': 'iron_ore.png',
        'COAL_ORE': 'coal_ore.png',
        'LOG': 'log_oak.png',
        'OAK_LOG': 'log_oak.png',
        'SPRUCE_LOG': 'log_spruce.png',
        'BIRCH_LOG': 'log_birch.png',
        'JUNGLE_LOG': 'log_jungle.png',
        'ACACIA_LOG': 'log_acacia.png',
        'DARK_OAK_LOG': 'log_big_oak.png',
        'LEAVES': 'leaves_oak.png',
        'OAK_LEAVES': 'leaves_oak.png',
        'GLASS': 'glass.png',
        'LAPIS_ORE': 'lapis_ore.png',
        'LAPIS_BLOCK': 'lapis_block.png',
        'SANDSTONE': 'sandstone_normal.png',
        'CHISELED_SANDSTONE': 'sandstone_carved.png',
        'SMOOTH_SANDSTONE': 'sandstone_smooth.png',
        
        // 羊毛方块
        'WHITE_WOOL': 'wool_colored_white.png',
        'ORANGE_WOOL': 'wool_colored_orange.png',
        'MAGENTA_WOOL': 'wool_colored_magenta.png',
        'LIGHT_BLUE_WOOL': 'wool_colored_light_blue.png',
        'YELLOW_WOOL': 'wool_colored_yellow.png',
        'LIME_WOOL': 'wool_colored_lime.png',
        'PINK_WOOL': 'wool_colored_pink.png',
        'GRAY_WOOL': 'wool_colored_gray.png',
        'LIGHT_GRAY_WOOL': 'wool_colored_silver.png',
        'CYAN_WOOL': 'wool_colored_cyan.png',
        'PURPLE_WOOL': 'wool_colored_purple.png',
        'BLUE_WOOL': 'wool_colored_blue.png',
        'BROWN_WOOL': 'wool_colored_brown.png',
        'GREEN_WOOL': 'wool_colored_green.png',
        'RED_WOOL': 'wool_colored_red.png',
        'BLACK_WOOL': 'wool_colored_black.png',
        
        // 矿物和宝石方块
        'GOLD_BLOCK': 'gold_block.png',
        'IRON_BLOCK': 'iron_block.png',
        'DIAMOND_ORE': 'diamond_ore.png',
        'DIAMOND_BLOCK': 'diamond_block.png',
        'EMERALD_ORE': 'emerald_ore.png',
        'EMERALD_BLOCK': 'emerald_block.png',
        'REDSTONE_ORE': 'redstone_ore.png',
        'NETHER_QUARTZ_ORE': 'quartz_ore.png',
        'QUARTZ_BLOCK': 'quartz_block_side.png',
        
        // 装饰和特殊方块
        'BRICK': 'brick.png',
        'BRICK_BLOCK': 'brick.png',
        'BRICKS': 'brick.png',
        'BOOKSHELF': 'bookshelf.png',
        'MOSSY_COBBLESTONE': 'cobblestone_mossy.png',
        'OBSIDIAN': 'obsidian.png',
        'NETHERRACK': 'netherrack.png',
        'SOUL_SAND': 'soul_sand.png',
        'GLOWSTONE': 'glowstone.png',
        'SEA_LANTERN': 'sea_lantern.png',
        'PRISMARINE': 'prismarine_rough.png',
        'DARK_PRISMARINE': 'prismarine_dark.png',
        'PRISMARINE_BRICKS': 'prismarine_bricks.png',
        'MYCELIUM': 'mycelium_top.png',
        'END_STONE': 'end_stone.png',
        'PURPUR_BLOCK': 'purpur_block.png',
        'PURPUR_PILLAR': 'purpur_pillar.png',
        
        // 机械和红石方块
        'CRAFTING_TABLE': 'crafting_table_top.png',
        'FURNACE': 'furnace_front_off.png',
        'DISPENSER': 'dispenser_front_horizontal.png',
        'DROPPER': 'dropper_front_horizontal.png',
        'PISTON': 'piston_top_normal.png',
        'STICKY_PISTON': 'piston_top_sticky.png',
        'TNT': 'tnt_side.png',
        'REDSTONE_LAMP': 'redstone_lamp_off.png',
        'REDSTONE_TORCH': 'redstone_torch_on.png',
        
        // 其他重要方块
        'ICE': 'ice.png',
        'PACKED_ICE': 'ice.png',
        'SNOW': 'snow.png',
        'SNOW_BLOCK': 'snow.png',
        'CLAY': 'clay.png',
        'PUMPKIN': 'pumpkin_side.png',
        'JACK_O_LANTERN': 'pumpkin_face_on.png',
        'MELON_BLOCK': 'melon_side.png',
        'CACTUS': 'cactus_side.png',
        'HAY_BLOCK': 'hay_block_side.png',
        'COBWEB': 'web.png',
        'SPONGE': 'sponge.png',
        'WET_SPONGE': 'sponge_wet.png',
        'CHORUS_FLOWER': 'chorus_flower.png',
        'CHORUS_PLANT': 'chorus_plant.png',
        'WOOL': 'wool_colored_white.png',
    };
    
    // 获取物品图标的URL
    function getItemIconUrl(itemType, damage) {
        // 特殊处理SKULL_ITEM
        if (itemType === 'SKULL_ITEM') {
            // SKULL_ITEM类型映射:
            // 0: skeleton, 1: wither, 2: zombie, 3: player, 4: creeper
            const skullType = damage || 0;
            switch (skullType) {
                case 0: return ITEM_ICONS_BASE_PATH + 'skull_skeleton.png';
                case 1: return ITEM_ICONS_BASE_PATH + 'skull_wither.png';
                case 2: return ITEM_ICONS_BASE_PATH + 'skull_zombie.png';
                case 3: return ITEM_ICONS_BASE_PATH + 'skull_char.png';
                case 4: return ITEM_ICONS_BASE_PATH + 'skull_creeper.png';
                default: return ITEM_ICONS_BASE_PATH + 'skull_skeleton.png';
            }
        }
        
        // 特殊处理龙息(DRAGONS_BREATH可能是API中的拼写)
        if (itemType === 'DRAGONS_BREATH') {
            return ITEM_ICONS_BASE_PATH + 'dragon_breath.png';
        }
        
        // 处理其他物品元数据
        if (damage !== undefined && damage !== null && damage > 0) {
            // 尝试使用带元数据的物品ID
            const itemWithDamage = `${itemType}:${damage}`;
            
            // 检查是否有带特定元数据的映射
            if (itemIconMapping[itemWithDamage]) {
                return ITEM_ICONS_BASE_PATH + itemIconMapping[itemWithDamage];
            }
        }
        
        // 检查物品是否存在于图标映射中
        if (itemIconMapping[itemType]) {
            return ITEM_ICONS_BASE_PATH + itemIconMapping[itemType];
        }
        
        // 检查是否存在于方块映射中
        if (blockMapping[itemType]) {
            return BLOCK_ICONS_BASE_PATH + blockMapping[itemType];
        }
        
        // 尝试匹配一些特殊的物品命名规则
        if (itemType.endsWith('_SPAWN_EGG')) {
            return ITEM_ICONS_BASE_PATH + 'spawn_egg.png';
        }
        
        if (itemType.startsWith('MUSIC_DISC')) {
            return ITEM_ICONS_BASE_PATH + 'record_11.png'; // 默认唱片图标
        }
        
        // 黄金装备特殊处理
        if (itemType === 'GOLD_HELMET') {
            return ITEM_ICONS_BASE_PATH + 'golden_helmet.png';
        }
        if (itemType === 'GOLD_CHESTPLATE') {
            return ITEM_ICONS_BASE_PATH + 'golden_chestplate.png';
        }
        if (itemType === 'GOLD_LEGGINGS') {
            return ITEM_ICONS_BASE_PATH + 'golden_leggings.png';
        }
        if (itemType === 'GOLD_BOOTS') {
            return ITEM_ICONS_BASE_PATH + 'golden_boots.png';
        }
        
        // 默认物品图标（禁止标志）
        return ITEM_ICONS_BASE_PATH + 'barrier.png';
    }
    
    // Minecraft item names translation
    const itemTranslations = {
        // 工具和武器
        'WOODEN_SWORD': '木剑',
        'STONE_SWORD': '石剑',
        'IRON_SWORD': '铁剑',
        'GOLDEN_SWORD': '金剑',
        'DIAMOND_SWORD': '钻石剑',
        'WOODEN_PICKAXE': '木镐',
        'STONE_PICKAXE': '石镐',
        'IRON_PICKAXE': '铁镐',
        'GOLDEN_PICKAXE': '金镐',
        'GOLD_PICKAXE': '金镐',
        'DIAMOND_PICKAXE': '钻石镐',
        'WOODEN_AXE': '木斧',
        'STONE_AXE': '石斧',
        'IRON_AXE': '铁斧',
        'GOLDEN_AXE': '金斧',
        'DIAMOND_AXE': '钻石斧',
        'WOODEN_SHOVEL': '木锹',
        'STONE_SHOVEL': '石锹',
        'IRON_SHOVEL': '铁锹',
        'GOLDEN_SHOVEL': '金锹',
        'DIAMOND_SHOVEL': '钻石锹',
        'WOODEN_HOE': '木锄',
        'STONE_HOE': '石锄',
        'IRON_HOE': '铁锄',
        'GOLDEN_HOE': '金锄',
        'DIAMOND_HOE': '钻石锄',
        'BOW': '弓',
        'FISHING_ROD': '钓鱼竿',
        'SHEARS': '剪刀',
        'FLINT_AND_STEEL': '打火石',
        'ELYTRA': '鞘翅',
        'SHIELD': '盾牌',
        
        // 盔甲
        'LEATHER_HELMET': '皮革头盔',
        'LEATHER_CHESTPLATE': '皮革胸甲',
        'LEATHER_LEGGINGS': '皮革护腿',
        'LEATHER_BOOTS': '皮革靴子',
        'CHAINMAIL_HELMET': '锁链头盔',
        'CHAINMAIL_CHESTPLATE': '锁链胸甲',
        'CHAINMAIL_LEGGINGS': '锁链护腿',
        'CHAINMAIL_BOOTS': '锁链靴子',
        'IRON_HELMET': '铁头盔',
        'IRON_CHESTPLATE': '铁胸甲',
        'IRON_LEGGINGS': '铁护腿',
        'IRON_BOOTS': '铁靴子',
        'GOLDEN_HELMET': '金头盔',
        'GOLDEN_CHESTPLATE': '金胸甲',
        'GOLDEN_LEGGINGS': '金护腿',
        'GOLDEN_BOOTS': '金靴子',
        'GOLD_HELMET': '金头盔',
        'GOLD_CHESTPLATE': '金胸甲',
        'GOLD_LEGGINGS': '金护腿',
        'GOLD_BOOTS': '金靴子',
        'DIAMOND_HELMET': '钻石头盔',
        'DIAMOND_CHESTPLATE': '钻石胸甲',
        'DIAMOND_LEGGINGS': '钻石护腿',
        'DIAMOND_BOOTS': '钻石靴子',
        
        // 常见方块
        'STONE': '石头',
        'GRASS': '草方块',
        'GRASS_BLOCK': '草方块',
        'DIRT': '泥土',
        'COBBLESTONE': '圆石',
        'WOOD': '木板',
        'OAK_PLANKS': '橡木木板',
        'SPRUCE_PLANKS': '云杉木板',
        'BIRCH_PLANKS': '白桦木板',
        'JUNGLE_PLANKS': '丛林木板',
        'ACACIA_PLANKS': '金合欢木板',
        'DARK_OAK_PLANKS': '深色橡木木板',
        'SAPLING': '树苗',
        'BEDROCK': '基岩',
        'WATER': '水',
        'LAVA': '岩浆',
        'SAND': '沙子',
        'GRAVEL': '沙砾',
        'GOLD_ORE': '金矿石',
        'IRON_ORE': '铁矿石',
        'COAL_ORE': '煤矿石',
        'LOG': '原木',
        'OAK_LOG': '橡木原木',
        'SPRUCE_LOG': '云杉原木',
        'BIRCH_LOG': '白桦原木',
        'JUNGLE_LOG': '丛林原木',
        'ACACIA_LOG': '金合欢原木',
        'DARK_OAK_LOG': '深色橡木原木',
        'LEAVES': '树叶',
        'OAK_LEAVES': '橡木树叶',
        'GLASS': '玻璃',
        'LAPIS_ORE': '青金石矿石',
        'LAPIS_BLOCK': '青金石块',
        'SANDSTONE': '砂岩',
        'CHISELED_SANDSTONE': '錾制砂岩',
        'SMOOTH_SANDSTONE': '平滑砂岩',
        'BED': '床',
        'COBWEB': '蜘蛛网',
        'PISTON': '活塞',
        
        // 羊毛
        'WOOL': '羊毛',
        'WHITE_WOOL': '白色羊毛',
        'ORANGE_WOOL': '橙色羊毛',
        'MAGENTA_WOOL': '品红色羊毛',
        'LIGHT_BLUE_WOOL': '淡蓝色羊毛',
        'YELLOW_WOOL': '黄色羊毛',
        'LIME_WOOL': '黄绿色羊毛',
        'PINK_WOOL': '粉红色羊毛',
        'GRAY_WOOL': '灰色羊毛',
        'LIGHT_GRAY_WOOL': '淡灰色羊毛',
        'CYAN_WOOL': '青色羊毛',
        'PURPLE_WOOL': '紫色羊毛',
        'BLUE_WOOL': '蓝色羊毛',
        'BROWN_WOOL': '棕色羊毛',
        'GREEN_WOOL': '绿色羊毛',
        'RED_WOOL': '红色羊毛',
        'BLACK_WOOL': '黑色羊毛',
        
        // 矿物方块
        'GOLD_BLOCK': '金块',
        'IRON_BLOCK': '铁块',
        'DIAMOND_ORE': '钻石矿石',
        'DIAMOND_BLOCK': '钻石块',
        'EMERALD_ORE': '绿宝石矿石',
        'EMERALD_BLOCK': '绿宝石块',
        'REDSTONE_ORE': '红石矿石',
        'NETHER_QUARTZ_ORE': '下界石英矿石',
        'QUARTZ_BLOCK': '石英块',
        
        // 建筑方块
        'BRICK': '砖块',
        'BRICK_BLOCK': '砖块',
        'BRICKS': '砖块',
        'BOOKSHELF': '书架',
        'MOSSY_COBBLESTONE': '苔石',
        'OBSIDIAN': '黑曜石',
        'NETHERRACK': '地狱岩',
        'SOUL_SAND': '灵魂沙',
        'GLOWSTONE': '萤石',
        'SEA_LANTERN': '海晶灯',
        
        // 特殊方块
        'MOB_SPAWNER': '刷怪箱',
        'CHEST': '箱子',
        'FURNACE': '熔炉',
        'CRAFTING_TABLE': '工作台',
        'TNT': 'TNT',
        'SPONGE': '海绵',
        'ICE': '冰',
        'SNOW': '雪',
        'CLAY': '粘土',
        'JUKEBOX': '唱片机',
        'PUMPKIN': '南瓜',
        'JACK_O_LANTERN': '南瓜灯',
        'TORCH': '火把',
        'CLAY_BRICK': '红砖',
        'SKULL_ITEM': '头颅',
        
        // 食物和消耗品
        'APPLE': '苹果',
        'GOLDEN_APPLE': '金苹果',
        'BREAD': '面包',
        'PORKCHOP': '生猪排',
        'COOKED_PORKCHOP': '熟猪排',
        'GRILLED_PORK': '熟猪排',
        'COOKIE': '曲奇',
        'MELON': '西瓜片',
        'BEEF': '生牛排',
        'COOKED_BEEF': '熟牛排',
        'RAW_BEEF': '生牛排',
        'CHICKEN': '生鸡肉',
        'COOKED_CHICKEN': '熟鸡肉',
        'RAW_CHICKEN': '生鸡肉',
        'CARROT': '胡萝卜',
        'POTATO': '马铃薯',
        'POTATO_ITEM': '马铃薯',
        'BAKED_POTATO': '烤马铃薯',
        'POISONOUS_POTATO': '毒马铃薯',
        'CHORUS_FRUIT': '紫颂果',
        'CHORUS_FRUIT_POPPED': '爆裂紫颂果',
        'BEETROOT': '甜菜根',
        'BEETROOT_SOUP': '甜菜汤',
        'RABBIT_STEW': '兔肉煲',
        'MUSHROOM_STEW': '蘑菇煲',
        'CAKE': '蛋糕',
        'PUMPKIN_PIE': '南瓜派',
        'ROTTEN_FLESH': '腐肉',
        'SPIDER_EYE': '蜘蛛眼',
        'FERMENTED_SPIDER_EYE': '发酵蜘蛛眼',
        'COD': '生鳕鱼',
        'COOKED_COD': '熟鳕鱼',
        'RAW_FISH': '生鱼',
        'COOKED_FISH': '熟鱼',
        'SALMON': '生鲑鱼',
        'COOKED_SALMON': '熟鲑鱼',
        'TROPICAL_FISH': '热带鱼',
        'PUFFERFISH': '河豚',
        'RABBIT': '生兔肉',
        'COOKED_RABBIT': '熟兔肉',
        'MUTTON': '生羊肉',
        'COOKED_MUTTON': '熟羊肉',
        
        // 其它物品
        'ARROW': '箭',
        'COAL': '煤炭',
        'DIAMOND': '钻石',
        'IRON_INGOT': '铁锭',
        'GOLD_INGOT': '金锭',
        'EMERALD': '绿宝石',
        'STICK': '木棍',
        'STRING': '线',
        'FEATHER': '羽毛',
        'GUNPOWDER': '火药',
        'SULPHUR': '火药',
        'WHEAT': '小麦',
        'WHEAT_SEEDS': '小麦种子',
        'FLINT': '燧石',
        'REDSTONE': '红石',
        'GLOWSTONE_DUST': '荧石粉',
        'EGG': '鸡蛋',
        'COMPASS': '指南针',
        'CLOCK': '时钟',
        'SUGAR': '糖',
        'BOWL': '碗',
        'BUCKET': '桶',
        'MILK_BUCKET': '牛奶桶',
        'WATER_BUCKET': '水桶',
        'LAVA_BUCKET': '熔岩桶',
        'ENDER_PEARL': '末影珍珠',
        'ENDER_EYE': '末影之眼',
        'EXPERIENCE_BOTTLE': '附魔之瓶',
        'BLAZE_ROD': '烈焰棒',
        'BLAZE_POWDER': '烈焰粉',
        'MAGMA_CREAM': '岩浆膏',
        'BREWING_STAND': '酿造台',
        'CAULDRON': '炼药锅',
        'BOOK': '书',
        'ENCHANTED_BOOK': '附魔书',
        'WRITTEN_BOOK': '成书',
        'PAPER': '纸',
        'QUARTZ': '下界石英',
        'NETHER_BRICK': '下界砖',
        'NETHER_STAR': '下界之星',
        'NETHER_WART': '地狱疣',
        'GHAST_TEAR': '恶魂之泪',
        'SADDLE': '鞍',
        'IRON_HORSE_ARMOR': '铁马铠',
        'GOLDEN_HORSE_ARMOR': '金马铠',
        'DIAMOND_HORSE_ARMOR': '钻石马铠',
        'MINECART': '矿车',
        'OAK_BOAT': '橡木船',
        'LEAD': '拴绳',
        'NAME_TAG': '命名牌',
        'SLIME_BALL': '粘液球',
        
        // 染料
        'BLACK_DYE': '黑色染料',
        'BLUE_DYE': '蓝色染料',
        'BROWN_DYE': '棕色染料',
        'CYAN_DYE': '青色染料',
        'GRAY_DYE': '灰色染料',
        'GREEN_DYE': '绿色染料',
        'LIGHT_BLUE_DYE': '淡蓝色染料',
        'LIME_DYE': '黄绿色染料',
        'MAGENTA_DYE': '品红色染料',
        'ORANGE_DYE': '橙色染料',
        'PINK_DYE': '粉红色染料',
        'PURPLE_DYE': '紫色染料',
        'RED_DYE': '红色染料',
        'LIGHT_GRAY_DYE': '淡灰色染料',
        'WHITE_DYE': '白色染料',
        'YELLOW_DYE': '黄色染料',
        'LAPIS_LAZULI': '青金石',
        'INK_SACK': '墨囊',
        'INK_SACK:0': '墨囊',
        'INK_SACK:1': '红色染料',
        'INK_SACK:2': '仙人掌绿染料',
        'INK_SACK:3': '可可豆',
        'INK_SACK:4': '青金石',
        'INK_SACK:5': '紫色染料',
        'INK_SACK:6': '青色染料',
        'INK_SACK:7': '淡灰色染料',
        'INK_SACK:8': '灰色染料',
        'INK_SACK:9': '粉红色染料',
        'INK_SACK:10': '黄绿色染料',
        'INK_SACK:11': '黄色染料',
        'INK_SACK:12': '淡蓝色染料',
        'INK_SACK:13': '品红色染料',
        'INK_SACK:14': '橙色染料',
        'INK_SACK:15': '骨粉',
        'PRISMARINE_CRYSTALS': '海晶砂粒',
        'PRISMARINE_SHARD': '海晶碎片',
        'WATCH': '时钟',
        'NETHER_BRICK_ITEM': '下界砖',
        'CLAY_BALL': '黏土球',
        
        // 药水和特殊物品
        'POTION': '药水',
        'SPLASH_POTION': '喷溅药水',
        'LINGERING_POTION': '滞留药水',
        'DRAGON_BREATH': '龙息',
        'DRAGONS_BREATH': '龙息',
        'SPAWN_EGG': '刷怪蛋',
        'FIREWORK_ROCKET': '烟花火箭',
        'FIREWORK_STAR': '烟花之星',
        'FIREWORK_CHARGE': '烟花之星',
        'FIREWORK': '烟花火箭',
        'MAP': '空地图',
        'FILLED_MAP': '地图',
        'BANNER': '旗帜',
        'SIGN': '告示牌',
        'NAME_TAG': '命名牌',
        'MUSIC_DISC_11': '11号唱片',
        'MUSIC_DISC_13': '13号唱片',
        'MUSIC_DISC_BLOCKS': '方块唱片',
        'MUSIC_DISC_CAT': '猫唱片',
        'MUSIC_DISC_CHIRP': '吱吱唱片',
        'MUSIC_DISC_FAR': '遥远唱片',
        'MUSIC_DISC_MALL': '商场唱片',
        'MUSIC_DISC_MELLOHI': '马勒基唱片',
        'MUSIC_DISC_STAL': '静谧唱片',
        'MUSIC_DISC_STRAD': '斯特拉德唱片',
        'MUSIC_DISC_WAIT': '等待唱片',
        'MUSIC_DISC_WARD': '卫道士唱片',
        'SKELETON_SKULL': '骷髅头',
        'WITHER_SKELETON_SKULL': '凋零骷髅头',
        'ZOMBIE_HEAD': '僵尸头',
        'PLAYER_HEAD': '玩家头',
        'CREEPER_HEAD': '苦力怕头',
        'LEAD': '拴绳',
        'INK_SACK': '墨囊',
        'INK_SACK:0': '黑色墨囊',
        'INK_SACK:1': '红色墨囊',
        'INK_SACK:2': '绿色墨囊',
        'INK_SACK:3': '棕色墨囊',
        'INK_SACK:4': '蓝色墨囊',
        'INK_SACK:5': '紫色墨囊',
        'INK_SACK:6': '青色墨囊',
        'INK_SACK:7': '灰色墨囊',
        'INK_SACK:8': '粉色墨囊',
        'INK_SACK:9': '黄色墨囊',
        'INK_SACK:10': '浅蓝色墨囊',
        'INK_SACK:11': '绿色墨囊',
        'INK_SACK:12': '橙色墨囊',
        'INK_SACK:13': '红色墨囊',
        'INK_SACK:14': '紫色墨囊',
        'INK_SACK:15': '白色墨囊',
        'PRISMARINE_CRYSTALS': '海晶砂粒',
        'PRISMARINE_SHARD': '海晶碎片',
        'WATCH': '时钟',
        'NETHER_BRICK_ITEM': '下界砖',
        'CLAY_BALL': '黏土球',
    };
    
    // Translate item names
    function translateItemName(englishName, damage) {
        // 特殊处理龙息
        if (englishName === 'DRAGONS_BREATH') {
            return '龙息';
        }
        
        // 处理带有元数据/损害值的物品
        if (damage !== undefined && damage !== null && damage > 0) {
            // 尝试使用带有元数据的物品ID
            const itemWithDamage = `${englishName}:${damage}`;
            
            // 检查是否存在带有特定元数据的翻译
            if (itemTranslations[itemWithDamage]) {
                return itemTranslations[itemWithDamage];
            }
        }
        
        // 尝试使用基本物品名称
        return itemTranslations[englishName] || englishName;
    }

    // Convert Minecraft color codes to HTML
    function convertMinecraftColors(text) {
        if (!text) return '';
        
        // Split the text into segments based on color codes
        const segments = text.split('§');
        if (segments.length === 1) return text; // No color codes found
        
        let html = segments[0]; // Add first segment without color
        
        // Process each colored segment
        for (let i = 1; i < segments.length; i++) {
            const segment = segments[i];
            if (segment.length > 0) {
                const colorCode = segment[0].toLowerCase();
                const text = segment.substring(1);
                
                if (colorCode in colorCodes) {
                    html += `<span style="color: ${colorCodes[colorCode]}">${text}</span>`;
                } else if (colorCode === 'l') {
                    html += `<span style="font-weight: bold">${text}</span>`;
                } else if (colorCode === 'n') {
                    html += `<span style="text-decoration: underline">${text}</span>`;
                } else if (colorCode === 'o') {
                    html += `<span style="font-style: italic">${text}</span>`;
                } else if (colorCode === 'k') {
                    html += `<span class="obfuscated">${text}</span>`;
                } else if (colorCode === 'm') {
                    html += `<span style="text-decoration: line-through">${text}</span>`;
                } else if (colorCode === 'r') {
                    html += `<span style="color: inherit; font-weight: normal; text-decoration: none; font-style: normal">${text}</span>`;
                } else {
                    html += text;
                }
            }
        }
        
        return html;
    }

    // 动态获取服务器地址
    const serverAddress = window.location.href.split('/')[2];
    console.log('服务器地址:', serverAddress);
    
    // Create inventory slots
    for (let i = 0; i < 36; i++) {
        const slot = document.createElement('div');
        slot.className = 'item-slot';
        slot.setAttribute('data-tooltip', '');
        inventorySlots.appendChild(slot);
    }
    
    // 更新服务器列表
    function updateServerList() {
        console.log('更新服务器列表...');
        
        // 设置最后更新时间戳
        lastServerListUpdate = Date.now();
        
        // 显示加载中
        if (servers.length === 0) {
            playerList.innerHTML = '<div class="loading">正在加载服务器列表...</div>';
        }
        
        fetch('/api/servers')
            .then(response => {
                if (!response.ok) {
                    throw new Error('HTTP错误，状态: ' + response.status);
                }
                return response.text();
            })
            .then(text => {
                if (!text || text.trim() === '') {
                    throw new Error('服务器返回了空响应');
                }
                
                try {
                    return JSON.parse(text);
                } catch (e) {
                    console.error("JSON解析错误:", e);
                    throw new Error('无法解析JSON响应: ' + e.message);
                }
            })
            .then(data => {
                console.log('获取到服务器列表:', data);
                
                // 过滤出有玩家在线的服务器（在线人数 >= 1）
                const allServers = data.servers || [];
                servers = allServers.filter(server => {
                    // 确保服务器对象有效且玩家数大于等于1
                    return server && typeof server.playerCount === 'number' && server.playerCount >= 1;
                });
                
                console.log('过滤后的服务器列表（仅显示有人在线的服务器）:', servers);
                
                // 计算总在线人数
                const totalPlayerCount = calculateTotalPlayerCount(allServers); // 使用所有服务器计算总人数
                
                // 更新标题显示总在线人数
                const playerListTitle = document.querySelector('.player-list h2');
                if (playerListTitle) {
                    playerListTitle.textContent = `在线玩家 (${totalPlayerCount})`;
                }
                
                // 检查服务器列表是否为空
                if (!servers || servers.length === 0) {
                    serverSelectorHeader.querySelector('h3').textContent = '选择子服: 暂无玩家在线';
                    playerList.innerHTML = '<div class="notice">当前没有玩家在线的服务器</div>';
                    return;
                }
                
                // 保存当前选择的服务器名称，用于后续保持选择状态
                const previouslySelectedServer = currentServer;
                
                // 保存serverList的当前滚动位置
                const scrollPosition = serverList.scrollTop;
                
                serverList.innerHTML = '';
                
                // 变量跟踪是否找到了之前选择的服务器
                let foundPreviousServer = false;
                
                // 显示每个服务器
                servers.forEach(server => {
                    // 确保服务器对象有效
                    if (!server || !server.name) {
                        console.warn('收到无效服务器数据:', server);
                        return;
                    }
                    
                    const serverItem = document.createElement('div');
                    serverItem.className = 'server-item';
                    serverItem.innerHTML = `
                        <span class="server-name">${server.name}</span>
                        <span class="player-count">${server.playerCount || 0} 玩家</span>
                    `;
                    
                    // 如果是之前选中的服务器，添加active类并标记为已找到
                    if (previouslySelectedServer === server.name) {
                        serverItem.classList.add('active');
                        foundPreviousServer = true;
                    }
                    
                    // 点击选择服务器 - 使用函数声明来确保事件正确绑定
                    serverItem.onclick = function() {
                        console.log(`选择服务器: ${server.name}`);
                        
                        // 移除所有项的active类
                        document.querySelectorAll('.server-item').forEach(item => {
                            item.classList.remove('active');
                        });
                        
                        // 添加active类到当前项
                        serverItem.classList.add('active');
                        
                        // 更新当前服务器
                        currentServer = server.name;
                        serverSelectorHeader.querySelector('h3').textContent = `选择子服: ${server.name}`;
                        
                        // 重置玩家详情查看状态
                        isViewingPlayerDetails = false;
                        
                        // 点击服务器项后关闭服务器列表
                        serverList.classList.remove('active');
                        serverSelectorHeader.querySelector('.arrow-icon').classList.remove('active');
                        
                        // 强制更新玩家列表（忽略用户交互状态）
                        forceUpdatePlayerList(server.name);
                        
                        // 标记用户正在交互，防止自动刷新干扰
                        startUserInteraction();
                        
                        // 更新最后的玩家列表更新时间戳
                        lastPlayerListUpdate = Date.now();
                    };
                    
                    serverList.appendChild(serverItem);
                });
                
                // 如果没有之前选择的服务器或者没有找到之前选择的服务器，并且有服务器可选
                if (!previouslySelectedServer && servers.length > 0) {
                    currentServer = servers[0].name;
                    const firstServerItem = document.querySelector('.server-item');
                    if (firstServerItem) {
                        firstServerItem.classList.add('active');
                        serverSelectorHeader.querySelector('h3').textContent = `选择子服: ${currentServer}`;
                        forceUpdatePlayerList(currentServer);
                        lastPlayerListUpdate = Date.now();
                    }
                } 
                // 如果之前有选择但在新列表中未找到，保持当前选择不变
                else if (previouslySelectedServer && !foundPreviousServer) {
                    console.log(`之前选择的服务器 ${previouslySelectedServer} 在更新后未找到，保持当前选择状态`);
                    currentServer = previouslySelectedServer;
                }
                
                // 恢复滚动位置
                serverList.scrollTop = scrollPosition;
            })
            .catch(error => {
                console.error('获取服务器列表时发生错误:', error);
                // 错误时不清空列表，只添加错误信息
                const errorMsg = document.createElement('div');
                errorMsg.className = 'server-item error';
                errorMsg.textContent = '加载服务器列表失败';
                
                // 只有在列表为空时才添加错误信息
                if (serverList.children.length === 0) {
                    serverList.appendChild(errorMsg);
                    
                    // 如果没有服务器数据，显示错误消息在玩家列表区域
                    if (servers.length === 0) {
                        playerList.innerHTML = '<div class="error">无法加载服务器列表，请检查连接或刷新页面</div>';
                        serverSelectorHeader.querySelector('h3').textContent = '选择子服: 加载失败';
                    }
                }
                
                // 30秒后自动重试
                setTimeout(() => {
                    updateServerList();
                }, 30000);
            });
    }

    // 计算所有服务器的总在线人数
    function calculateTotalPlayerCount(serverList) {
        if (!serverList || !Array.isArray(serverList)) {
            return 0;
        }
        
        return serverList.reduce((total, server) => {
            // 确保playerCount是数字，如果不是则默认为0
            const count = server && typeof server.playerCount === 'number' ? server.playerCount : 0;
            return total + count;
        }, 0);
    }

    // 更新玩家列表
    function updatePlayerList(server) {
        // 设置最后更新时间戳
        lastPlayerListUpdate = Date.now();
        
        // 检查是否有有效的服务器名称
        if (!server) {
            console.log('无有效服务器，跳过玩家列表更新');
            playerList.innerHTML = '<div class="notice">请先选择一个子服</div>';
            return;
        }
        
        // 如果正在查看玩家详情，则不更新玩家列表
        if (isViewingPlayerDetails) {
            console.log('正在查看玩家详情，暂停玩家列表更新');
            return;
        }
        
        // 如果用户正在交互，延迟更新
        if (isUserInteracting) {
            console.log('用户正在交互，延迟玩家列表更新');
            return;
        }
        
        console.log(`更新玩家列表 - 服务器: ${server || '所有服务器'}`);
        
        // 显示加载中
        playerList.innerHTML = '<div class="loading">正在加载玩家列表...</div>';
        
        // 构建API URL
        let apiUrl = '/api/players';
        if (server) {
            apiUrl += `?server=${encodeURIComponent(server)}`;
        }
        
        console.log(`请求玩家列表API: ${apiUrl}`);
        
        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };
        
        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家列表超时');
            playerList.innerHTML = '<div class="error">加载玩家列表超时</div>';
        }, 10000);
        
        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) => 
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家列表API响应状态: ${response.status}`);
            
            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }
            
            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);
                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家列表数据:', data);
            renderPlayerList(data);
        })
        .catch(error => {
            console.error('获取玩家列表时发生错误:', error);
            playerList.innerHTML = `<div class="error">加载玩家列表失败: ${error.message}</div>`;
            
            // 5秒后自动重试，但只有在不是查看玩家详情时
            setTimeout(() => {
                if (!isViewingPlayerDetails && currentServer === server) {
                    console.log('自动重试强制加载玩家列表');
                    forceUpdatePlayerList(server);
                }
            }, 5000);
        });
    }
    
    // 强制更新玩家列表函数（不受用户交互状态影响）
    function forceUpdatePlayerList(server) {
        console.log(`强制更新玩家列表 - 服务器: ${server || '所有服务器'}`);
        
        // 检查是否有有效的服务器名称
        if (!server) {
            console.log('无有效服务器，跳过玩家列表更新');
            playerList.innerHTML = '<div class="notice">请先选择一个子服</div>';
            return;
        }
        
        // 移除判断isViewingPlayerDetails的代码，确保始终更新玩家列表
        
        // 显示加载中
        playerList.innerHTML = '<div class="loading">正在加载玩家列表...</div>';
        
        // 构建API URL
        let apiUrl = '/api/players';
        if (server) {
            apiUrl += `?server=${encodeURIComponent(server)}`;
        }
        
        console.log(`强制请求玩家列表API: ${apiUrl}`);
        
        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };
        
        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家列表超时');
            playerList.innerHTML = '<div class="error">加载玩家列表超时</div>';
        }, 10000);
        
        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) => 
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家列表API响应状态: ${response.status}`);
            
            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }
            
            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);
                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家列表数据:', data);
            
            if (!data) {
                playerList.innerHTML = '<div class="error">玩家列表数据无效</div>';
                return;
            }
            
            if (!Array.isArray(data.players)) {
                playerList.innerHTML = '<div class="error">玩家列表格式错误</div>';
                console.error('玩家列表数据格式错误:', data);
                return;
            }
            
            if (data.players.length === 0) {
                playerList.innerHTML = '<div class="notice">该服务器没有在线玩家</div>';
                return;
            }
            
            // 清空并重新生成玩家列表
            playerList.innerHTML = '';
            
            // 生成每个玩家的列表项
            data.players.forEach(player => {
                // 检查玩家数据类型并适当处理
                let playerName;
                
                if (typeof player === 'string') {
                    // 如果player是字符串，直接使用
                    playerName = player;
                } else if (player && player.name) {
                    // 如果player是对象并且有name属性
                    playerName = player.name;
                } else {
                    // 无效的玩家数据
                    console.warn('检测到无效的玩家数据:', player);
                    return;
                }
                
                const playerItem = document.createElement('li');
                playerItem.innerHTML = `
                    <div class="player-name">${playerName}</div>
                `;
                
                // 添加点击事件 - 查看玩家详情
                playerItem.addEventListener('click', () => {
                    // 设置正在查看玩家详情标志
                    isViewingPlayerDetails = true;
                    
                    // 移除其他玩家的活动状态
                    document.querySelectorAll('#playerList li').forEach(item => {
                        item.classList.remove('active');
                    });
                    
                    // 标记当前玩家为活动状态
                    playerItem.classList.add('active');
                    
                    // 加载玩家详情
                    loadPlayerInfo(playerName, currentServer);
                });
                
                playerList.appendChild(playerItem);
            });
        })
        .catch(error => {
            console.error('获取玩家列表时发生错误:', error);
            playerList.innerHTML = `<div class="error">加载玩家列表失败: ${error.message}</div>`;
        });
    }

    // 载入玩家详情
    function loadPlayerInfo(playerName, serverName = null) {
        console.log(`加载玩家详情: ${playerName}, 服务器: ${serverName || '默认服务器'}`);
        
        // 设置正在查看玩家详情状态
        isViewingPlayerDetails = true;
        
        // 显示玩家信息区域
        document.querySelector('.player-info').classList.add('active');
        
        // 移除创建和显示返回按钮的代码，不再需要"返回玩家列表"按钮
        
        // 清空当前物品详情
        inventorySlots.querySelectorAll('.item-tooltip').forEach(tooltip => {
            tooltip.remove();
        });
        
        // 清除玩家信息区域的旧内容
        const playerInfoContent = document.querySelector('.player-info-content');
        if (playerInfoContent) {
            playerInfoContent.innerHTML = '<div class="loading">正在加载玩家信息...</div>';
        }
        
        // 设置玩家名称标题
        const playerNameHeader = document.querySelector('.player-info-header h3');
        if (playerNameHeader) {
            playerNameHeader.textContent = playerName;
        }
        
        // 构建API URL - 修复格式为/api/player/[玩家名]?server=[服务器名]
        let apiUrl = `/api/player/${encodeURIComponent(playerName)}`;
        if (serverName) {
            apiUrl += `?server=${encodeURIComponent(serverName)}`;
        }
        
        console.log(`请求玩家详情API: ${apiUrl}`);
        
        // 请求配置
        const fetchOptions = {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            mode: 'same-origin',
            cache: 'no-cache',
            credentials: 'same-origin'
        };
        
        // 设置请求超时
        const timeout = setTimeout(() => {
            console.error('请求玩家详情超时');
            document.getElementById('playerStatus').innerHTML = 
                '<div class="error">加载玩家详情超时，请稍后重试</div>';
        }, 10000);
        
        // 发起请求与超时竞赛
        Promise.race([
            fetch(apiUrl, fetchOptions),
            new Promise((_, reject) => 
                setTimeout(() => reject(new Error('请求超时')), 10000)
            )
        ])
        .then(response => {
            clearTimeout(timeout);
            console.log(`玩家详情API响应状态: ${response.status}`);
            
            if (!response.ok) {
                throw new Error('HTTP错误，状态: ' + response.status);
            }
            return response.text();
        })
        .then(text => {
            if (!text || text.trim() === '') {
                throw new Error('服务器返回了空响应');
            }
            
            try {
                return JSON.parse(text);
            } catch (e) {
                console.error("JSON解析错误:", e);
                throw new Error('无法解析JSON响应: ' + e.message);
            }
        })
        .then(data => {
            console.log('获取到玩家详情数据:', data);
            
            // 后端API返回格式检查
            if (data && !data.error) {
                updatePlayerStatus(data);
                
                // 更新装备栏
                if (data.armor) {
                    updateArmorSlots(data.armor);
                }
                
                // 更新物品栏
                if (data.inventory) {
                    updateInventorySlots(data.inventory, data.armor || [], data.mainHand, data.offHand);
                }
                
                // 更新主副手
                if (data.mainHand || data.offHand) {
                    updateHandSlots(data.mainHand, data.offHand);
                }
            } else {
                console.error('加载玩家详情失败:', data.error || '未知错误');
                document.getElementById('playerStatus').innerHTML = 
                    `<div class="error">加载失败: ${data.error || '未知错误'}</div>`;
            }
        })
        .catch(error => {
            clearTimeout(timeout);
            console.error('获取玩家详情时发生错误:', error);
            
            // 显示错误信息
            document.getElementById('playerStatus').innerHTML = 
                `<div class="error">加载玩家详情失败: ${error.message}</div>`;
        });
    }

    // 更新玩家状态栏
    function updatePlayerStatus(playerData) {
        // 更新玩家名称
        const playerNameElement = document.getElementById('playerNameStatus');
        if (playerNameElement) {
            playerNameElement.textContent = playerData.name || '--';
        }

        // 更新玩家等级
        const playerLevelElement = document.getElementById('playerLevelStatus');
        if (playerLevelElement && playerData.level !== undefined) {
            playerLevelElement.textContent = playerData.level || '0';
        }

        // 更新玩家血量和血量条
        const healthBar = document.getElementById('healthBar');
        const healthValue = document.getElementById('healthValue');
        if (healthBar && healthValue && playerData.health !== undefined && playerData.maxHealth !== undefined) {
            const healthPercent = (playerData.health / playerData.maxHealth) * 100;
            healthBar.style.width = `${healthPercent}%`;
            
            // 根据血量百分比改变颜色
            if (healthPercent <= 20) {
                healthBar.style.backgroundColor = '#f44336'; // 红色
            } else if (healthPercent <= 50) {
                healthBar.style.backgroundColor = '#ff9800'; // 橙色
                } else {
                healthBar.style.backgroundColor = '#4caf50'; // 绿色
            }
            
            healthValue.textContent = `${Math.round(playerData.health)}/${Math.round(playerData.maxHealth)}`;
        }

        // 更新玩家坐标
        if (playerData.location) {
            const x = playerData.location.x ? playerData.location.x.toFixed(1) : '0';
            const y = playerData.location.y ? playerData.location.y.toFixed(1) : '0';
            const z = playerData.location.z ? playerData.location.z.toFixed(1) : '0';
            
            const xElement = document.getElementById('playerCoordsX');
            const yElement = document.getElementById('playerCoordsY');
            const zElement = document.getElementById('playerCoordsZ');
            
            if (xElement) xElement.textContent = x;
            if (yElement) yElement.textContent = y;
            if (zElement) zElement.textContent = z;
        }
    }

    // Update armor slots with player data
    function updateArmorSlots(armor) {
        const armorSlots = document.querySelectorAll('#armorSlots .item-slot');
        
        // 清空所有盔甲槽
        armorSlots.forEach(slot => {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
        });
        
        // Minecraft的盔甲数组是：[靴子(0), 护腿(1), 胸甲(2), 头盔(3)]
        // HTML的元素顺序是：[头盔(0), 胸甲(1), 护腿(2), 靴子(3)]
        
        // 直接映射每个盔甲位置，不过滤null值
        for (let i = 0; i < armor.length && i < 4; i++) {
            const item = armor[i];
            if (item === null) continue; // 跳过空槽位，但不改变映射关系
            
            // 计算正确的映射索引：3-i 将盔甲数组索引转为HTML元素索引
            // 例如：盔甲[3](头盔) -> HTML[0]，盔甲[0](靴子) -> HTML[3]
            const mappedIndex = 3 - i;
            if (armorSlots[mappedIndex]) {
                updateSlot(armorSlots[mappedIndex], item);
            }
        }
    }

    // Update inventory slots with player data
    function updateInventorySlots(inventory, armor, mainHand, offHand) {
        const inventoryContainer = document.getElementById('inventorySlots');
        inventoryContainer.innerHTML = '';
        
        // 创建所有物品栏槽位
    for (let i = 0; i < 36; i++) {
        const slot = document.createElement('div');
        slot.className = 'item-slot';
            slot.dataset.index = i;
            inventoryContainer.appendChild(slot);
        }
        
        // 仅更新有物品的槽位
        const slots = document.querySelectorAll('#inventorySlots .item-slot');
        
        // 收集所有需要排除的装备物品信息
        const excludedItems = [];
        
        // 添加装备栏物品到排除列表
        if (armor && armor.length) {
            for (let item of armor) {
                if (item && item.type && item.type !== 'AIR') {
                    excludedItems.push(item);
                }
            }
        }
        
        // 添加主手物品到排除列表（如果不为空）
        if (mainHand && mainHand.type && mainHand.type !== 'AIR') {
            excludedItems.push(mainHand);
        }
        
        // 添加副手物品到排除列表（如果不为空）
        if (offHand && offHand.type && offHand.type !== 'AIR') {
            excludedItems.push(offHand);
        }
        
        inventory.forEach((item, index) => {
            // 只处理主物品栏的物品 (0-35)
            if (item && item.type && item.type !== 'AIR' && index < 36) {
                // 检查是否需要排除该物品（与装备栏或主副手相同）
                let shouldExclude = false;
                
                for (let excludedItem of excludedItems) {
                    // 比较物品类型和附加值（damage）
                    if (item.type === excludedItem.type && item.damage === excludedItem.damage) {
                        // 如果找到匹配，需要排除此物品
                        shouldExclude = true;
                        break;
                    }
                }
                
                // 只显示不是装备复制品的物品
                if (!shouldExclude) {
                    updateSlot(slots[index], item);
                }
            }
        });
    }

    // Update hand slots with player data
    function updateHandSlots(mainHand, offHand) {
        const mainHandSlot = document.querySelector('.item-slot.main-hand');
        const offHandSlot = document.querySelector('.item-slot.off-hand');
        
        if (mainHandSlot) {
            if (mainHand && mainHand.type && mainHand.type !== 'AIR') {
                updateSlot(mainHandSlot, mainHand);
            } else {
                mainHandSlot.innerHTML = '';
                mainHandSlot.classList.remove('has-item');
            }
        }
        
        if (offHandSlot) {
            if (offHand && offHand.type && offHand.type !== 'AIR') {
                updateSlot(offHandSlot, offHand);
            } else {
                offHandSlot.innerHTML = '';
                offHandSlot.classList.remove('has-item');
            }
        }
    }

    // Update individual slot with item data
    function updateSlot(slot, item) {
        if (!item) {
            slot.innerHTML = '';
            slot.classList.remove('has-item');
            return;
        }
        
        const itemType = item.type;
        const amount = item.amount || 1;
        const name = item.name || translateItemName(itemType, item.durability);
        const lore = item.lore || [];
        
        slot.classList.add('has-item');
        slot.innerHTML = '';
        
        const iconDiv = document.createElement('div');
        iconDiv.className = 'item-icon';
        iconDiv.style.backgroundImage = `url(${getItemIconUrl(itemType, item.durability)})`;
        slot.appendChild(iconDiv);
        
        if (amount > 1) {
            const countDiv = document.createElement('div');
            countDiv.className = 'item-count';
            countDiv.textContent = amount;
            iconDiv.appendChild(countDiv);
        }
        
        // 创建工具提示内容
        const tooltipContent = document.createElement('div');
        tooltipContent.className = 'tooltip-content';
        
        // 添加标题
        const titleDiv = document.createElement('div');
        titleDiv.className = 'tooltip-title';
        titleDiv.innerHTML = convertMinecraftColors(name);
        tooltipContent.appendChild(titleDiv);
        
        // 添加lore内容
        if (lore.length > 0) {
            const loreDiv = document.createElement('div');
            loreDiv.className = 'tooltip-lore';
            
            lore.forEach(line => {
                const lineDiv = document.createElement('div');
                lineDiv.innerHTML = convertMinecraftColors(line);
                lineDiv.style.color = '#AAAAAA';
                loreDiv.appendChild(lineDiv);
            });
            
            tooltipContent.appendChild(loreDiv);
        }
        
        slot.appendChild(tooltipContent);
        
        // 修改鼠标移入事件处理，使用fixed定位计算tooltip位置
        slot.addEventListener('mouseenter', () => {
            // 计算物品槽的位置
            const slotRect = slot.getBoundingClientRect();
            const tooltipContent = slot.querySelector('.tooltip-content');
            
            // 默认显示在物品槽上方
            let positionTop = slotRect.top - 10; // 距离物品槽上边缘10px
            let positionLeft = slotRect.left + slotRect.width / 2;
            
            // 设置tooltip位置为fixed，基于视口计算
            tooltipContent.style.bottom = '';
            tooltipContent.style.top = '';
            tooltipContent.classList.remove('tooltip-bottom');
            tooltipContent.style.transform = 'translateX(-50%)';
            
            // 显示tooltip后再检查是否需要调整位置
            // 强制浏览器重新计算尺寸以获取正确的tooltip大小
            tooltipContent.style.display = 'block';
            const tooltipRect = tooltipContent.getBoundingClientRect();
            
            // 检查顶部是否有足够空间
            if (positionTop - tooltipRect.height < 10) {
                // 如果顶部空间不足，显示在底部
                positionTop = slotRect.bottom + 10; // 距离物品槽下边缘10px
                tooltipContent.classList.add('tooltip-bottom');
            } else {
                // 正常显示在上方
                positionTop = positionTop - tooltipRect.height;
            }
            
            // 检查水平方向是否溢出
            if (positionLeft - tooltipRect.width / 2 < 10) {
                // 左侧空间不足
                positionLeft = 10 + tooltipRect.width / 2;
            } else if (positionLeft + tooltipRect.width / 2 > window.innerWidth - 10) {
                // 右侧空间不足
                positionLeft = window.innerWidth - 10 - tooltipRect.width / 2;
            }
            
            // 应用最终位置
            tooltipContent.style.top = `${positionTop}px`;
            tooltipContent.style.left = `${positionLeft}px`;
        });
        
        // 鼠标离开时隐藏tooltip
        slot.addEventListener('mouseleave', () => {
            const tooltipContent = slot.querySelector('.tooltip-content');
            tooltipContent.style.display = 'none';
        });
        
        // 添加点击事件监听器，显示物品详情弹窗
        slot.addEventListener('click', () => {
            showItemDetailModal(item, getItemIconUrl(itemType, item.durability));
        });
    }

    // 物品详情模态弹窗功能
    const modal = document.getElementById('itemDetailModal');
    const closeButton = document.querySelector('.close-button');
    
    // 关闭模态弹窗
    function closeModal() {
        // 关闭物品详情模态框
        const modal = document.getElementById('itemDetailModal');
        if (modal) {
            // 先移除active类触发CSS过渡效果
            modal.classList.remove('active');
            
            // 等待过渡动画完成后再隐藏元素
            setTimeout(() => {
                modal.style.display = 'none';
            }, 300); // 300毫秒是过渡时间，可根据CSS中定义的过渡时间调整
        }
        
        // 隐藏玩家信息区域
        const playerInfo = document.querySelector('.player-info');
        if (playerInfo) {
            playerInfo.classList.remove('active');
            
            // 重置交互状态
            isViewingPlayerDetails = false;
            
            // 短暂延迟后自动更新玩家列表
            setTimeout(() => {
                if (!isViewingPlayerDetails && Date.now() - lastPlayerListUpdate > 5000) {
                    updatePlayerList(currentServer);
                }
            }, 500);
        }
    }
    
    // 点击关闭按钮
    if (closeButton) {
        closeButton.addEventListener('click', closeModal);
    }
    
    // 点击模态弹窗外部区域也关闭
    window.addEventListener('click', (event) => {
        const modal = document.getElementById('itemDetailModal');
        if (event.target === modal) {
            closeModal();
        }
    });
    
    // ESC键关闭弹窗
    window.addEventListener('keydown', (event) => {
        const modal = document.getElementById('itemDetailModal');
        if (event.key === 'Escape' && modal && (modal.style.display === 'block' || modal.classList.contains('active'))) {
            closeModal();
        }
    });
    
    // 显示物品详情模态弹窗
    function showItemDetailModal(item, iconUrl) {
        const itemName = item.name || translateItemName(item.type, item.durability);
        const lore = item.lore || [];
        
        // 设置物品名称
        document.getElementById('itemDetailName').innerHTML = convertMinecraftColors(itemName);
        document.getElementById('itemDetailTitle').textContent = translateItemName(item.type, item.durability);
        
        // 设置物品图片
        document.getElementById('itemDetailImage').src = iconUrl;
        
        // 清空并设置LORE内容
        const loreContainer = document.getElementById('itemDetailLore');
        loreContainer.innerHTML = '';
        
        if (lore.length > 0) {
            lore.forEach(line => {
                const lineDiv = document.createElement('div');
                lineDiv.innerHTML = convertMinecraftColors(line);
                loreContainer.appendChild(lineDiv);
            });
        } else {
            // 如果没有LORE，显示基本信息
            const basicInfo = document.createElement('div');
            basicInfo.innerHTML = `
                <div>类型: ${item.type}</div>
                ${item.durability > 0 ? `<div>耐久度: ${item.durability}</div>` : ''}
                ${item.amount > 1 ? `<div>数量: ${item.amount}</div>` : ''}
            `;
            loreContainer.appendChild(basicInfo);
        }
        
        // 显示模态弹窗
        modal.style.display = 'block';
        modal.classList.add('active'); // 添加active类以应用CSS动画效果
    }

    // 确保页面加载时添加关闭按钮的点击事件
    document.addEventListener('DOMContentLoaded', () => {
        // 为关闭按钮添加事件
        const closeButtons = document.querySelectorAll('.close-button');
        closeButtons.forEach(button => {
            button.onclick = closeModal;
        });
        
        // 为玩家信息区域的关闭按钮添加单独的事件监听器
        const playerInfoCloseButton = document.querySelector('.player-info .close-button');
        if (playerInfoCloseButton) {
            playerInfoCloseButton.onclick = function() {
                console.log('关闭玩家详情');
                closeModal();
            };
        } else {
            console.log('警告：未找到玩家详情关闭按钮');
            // 不再动态创建关闭按钮，避免出现多余元素
        }
    });

    // 渲染玩家列表的通用函数
    function renderPlayerList(data) {
        console.log('渲染玩家列表数据:', data);
        
        if (!data) {
            playerList.innerHTML = '<div class="error">玩家列表数据无效</div>';
            return;
        }
        
        if (!Array.isArray(data.players)) {
            playerList.innerHTML = '<div class="error">玩家列表格式错误</div>';
            console.error('玩家列表数据格式错误:', data);
            return;
        }
        
        if (data.players.length === 0) {
            playerList.innerHTML = '<div class="notice">该服务器没有在线玩家</div>';
            return;
        }
        
        // 清空并重新生成玩家列表
        playerList.innerHTML = '';
        
        // 生成每个玩家的列表项
        data.players.forEach(player => {
            // 检查玩家数据类型并适当处理
            let playerName;
            
            if (typeof player === 'string') {
                // 如果player是字符串，直接使用
                playerName = player;
            } else if (player && player.name) {
                // 如果player是对象并且有name属性
                playerName = player.name;
            } else {
                // 无效的玩家数据
                console.warn('检测到无效的玩家数据:', player);
                return;
            }
            
            const playerItem = document.createElement('li');
            playerItem.innerHTML = `
                <div class="player-name">${playerName}</div>
            `;
            
            // 添加点击事件 - 查看玩家详情
            playerItem.addEventListener('click', () => {
                // 设置正在查看玩家详情标志
                isViewingPlayerDetails = true;
                
                // 移除其他玩家的活动状态
                document.querySelectorAll('#playerList li').forEach(item => {
                    item.classList.remove('active');
                });
                
                // 标记当前玩家为活动状态
                playerItem.classList.add('active');
                
                // 加载玩家详情
                loadPlayerInfo(playerName, currentServer);
            });
            
            playerList.appendChild(playerItem);
        });
    }
}); 