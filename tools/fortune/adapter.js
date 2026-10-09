// Turns raw Hypixel API data into the inputs Elite's farming-weight library expects, runs the
// library, and flattens its results into plain JSON for the mod's fortune screen.
// Bundled together with the library by build.mjs; exposed to Java as the global "Sentils".
import * as FW from 'farming-weight';

const { Crop, Stat } = FW;

const CROPS = [
	Crop.Wheat, Crop.Carrot, Crop.Potato, Crop.Pumpkin, Crop.Melon, Crop.Mushroom, Crop.Cactus,
	Crop.SugarCane, Crop.NetherWart, Crop.CocoaBeans, Crop.Sunflower, Crop.Moonflower, Crop.WildRose,
];

const FORTUNE_STATS = [Stat.FarmingFortune, Stat.Overbloom];

// Level-up tasks that mark a crop as exported to Carrolyn.
const EXPORT_TASKS = {
	[Crop.Carrot]: 'CARROLYN_EXPORT_CROP_EXPORTABLE_CARROTS',
	[Crop.CocoaBeans]: 'CARROLYN_EXPORT_CROP_SUPREME_CHOCOLATE_BAR',
	[Crop.Mushroom]: 'CARROLYN_EXPORT_CROP_HALF_EATEN_MUSHROOM',
	[Crop.NetherWart]: 'CARROLYN_EXPORT_CROP_WARTY',
	[Crop.Pumpkin]: 'CARROLYN_EXPORT_CROP_EXPIRED_PUMPKIN',
	[Crop.Wheat]: 'CARROLYN_EXPORT_CROP_FINE_FLOUR',
	[Crop.WildRose]: 'CARROLYN_EXPORT_CROP_PRICKLY_KISS',
};

const DNA_TASK = 'dna_analysis_rewardfarming_fortune_';

// Skill experience needed for each level, from 1 to 60.
const SKILL_XP = [
	50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000, 50000, 75000,
	100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000, 1200000, 1300000,
	1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000, 2200000, 2300000, 2400000, 2500000,
	2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000, 4300000, 4600000, 4900000, 5200000, 5500000,
	5800000, 6100000, 6400000, 6700000, 7000000,
];

// What the website assumes for the things Hypixel's API does not report.
const DEFAULT_SETTINGS = {
	communityCenter: 0,
	strength: 0,
	speed: 400,
	feastBurgers: 0,
	filledRosewaterFlask: 0,
	sprayedPlot: true,
	infestedPlotProbability: 0.2,
	temporaryFortune: {
		pestTurnIn: 0,
		harvestPotion: false,
		chocolateTruffle: false,
		centuryCake: true,
		springFilter: false,
		magic8Ball: false,
		flourSpray: false,
		anitaContest: false,
		celestialMasonJar: false,
		melonJuiceMixin: false,
		finnsFocaccia: false,
		stinkyCheesePotion: false,
	},
};

let session = null;

function skillLevel(xp, cap) {
	let level = 0;
	let left = xp || 0;
	while (level < cap && level < SKILL_XP.length && left >= SKILL_XP[level]) {
		left -= SKILL_XP[level];
		level++;
	}
	return level;
}

function camelKeys(source) {
	const result = {};
	for (const [key, value] of Object.entries(source || {})) {
		result[key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())] = value;
	}
	return result;
}

function num(value) {
	const number = Number(value);
	return Number.isFinite(number) ? number : 0;
}

function round(value) {
	return Math.round(num(value) * 100) / 100;
}

function commas(value) {
	return String(Math.round(num(value))).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

const RARITIES = ['Common', 'Uncommon', 'Rare', 'Epic', 'Legendary', 'Mythic', 'Divine'];
const RARITY_COLORS = ['§f', '§a', '§9', '§5', '§6', '§d', '§b'];

/**
 * Tools kept in the garden's farming toolkit come without a name or lore, which is where the
 * library reads an item's rarity from. Both are filled in from the library's own tool list.
 */
function completeToolkitItem(item) {
	if (item.name || item.lore) return item;
	const info = FW.FARMING_TOOLS[item.skyblockId];
	if (!info) return item;

	const attributes = item.attributes || {};
	let rarity = Math.max(0, RARITIES.indexOf(info.baseRarity));
	if (attributes.rarity_upgrades === '1') rarity = Math.min(rarity + 1, RARITIES.length - 1);
	const reforge = attributes.modifier ? itemName(attributes.modifier) + ' ' : '';
	return {
		...item,
		name: RARITY_COLORS[rarity] + reforge + info.name,
		attributes: { ...attributes, rarity: RARITIES[rarity].toUpperCase() },
	};
}

/** input: { member, garden, items, accessories }, all but the item lists straight from Hypixel. */
function load(inputJson) {
	const input = JSON.parse(inputJson);
	const member = input.member || {};
	const garden = input.garden || {};
	const items = (input.items || []).map(completeToolkitItem);

	const jacob = member.jacobs_contest || {};
	const perks = jacob.perks || {};
	const playerData = member.player_data || {};
	const easter = (member.events || {}).easter || {};
	const tasks = (member.leveling || {}).completed_tasks || [];

	const exportableCrops = {};
	for (const [crop, task] of Object.entries(EXPORT_TASKS)) {
		if (tasks.includes(task)) exportableCrops[crop] = true;
	}

	let dnaMilestone = 0;
	for (const task of (member.objectives || {}).tutorial || []) {
		if (task.startsWith(DNA_TASK)) dnaMilestone = Math.max(dnaMilestone, num(task.slice(DNA_TASK.length)));
	}

	// The library looks personal bests up by crop name ("NetherWart"); Hypixel lists them by item id.
	const personalBests = {};
	for (const [key, amount] of Object.entries(jacob.personal_bests || {})) {
		const crop = FW.getCropFromContestKey(key) || FW.getCropFromName(key);
		personalBests[crop ? FW.getCropDisplayName(crop).replace(/ /g, '') : key] = amount;
	}

	const farmingXp = num((playerData.experience || {}).SKILL_FARMING);
	const pets = ((member.pets_data || {}).pets || []).map((pet) => ({ ...pet, uuid: pet.uuid || pet.uniqueId }));

	session = {
		items,
		pets,
		base: {
			accessories: input.accessories || [],
			refinedTruffles: num(easter.refined_dark_cacao_truffles),
			personalBestsUnlocked: perks.personal_bests === true,
			personalBests,
			anitaBonus: num(perks.double_drops),
			plots: garden.unlocked_plots_ids || [],
			farmingXp,
			farmingLevel: skillLevel(farmingXp, 50 + num(perks.farming_level_cap)),
			bestiaryKills: (member.bestiary || {}).kills || {},
			uniqueVisitors: num((garden.commission_data || {}).unique_npcs_served),
			exportableCrops,
			dnaMilestone,
			attributes: (member.attributes || {}).stacks || {},
			chips: camelKeys(playerData.garden_chips),
			chipRarities: {},
			perks: playerData.perks,
			wrigglingLarva: num((member.garden_player_data || {}).larva_consumed),
			milestones: FW.getCropMilestoneLevels(garden.resources_collected || {}),
			cropUpgrades: FW.getCropUpgrades(garden.crop_upgrade_levels || {}),
			gardenLevel: FW.getGardenLevel(num(garden.garden_experience)).level,
			cocoaFortuneUpgrade: num((easter.shop || {}).cocoa_fortune_upgrades),
			zorro: {
				enabled: !!(easter.rabbits && easter.rabbits.zorro !== undefined),
				mode: 'normal',
			},
		},
	};

	return JSON.stringify({
		crops: CROPS.map((crop) => ({ id: crop, name: FW.getCropDisplayName(crop) })),
		// Hypixel leaves these out when the player has that part of their API switched off.
		hasGarden: !!input.garden,
		hasInventory: !!(member.inventory && member.inventory.inv_contents),
		hasSkills: (playerData.experience || {}).SKILL_FARMING !== undefined,
	});
}

function statLabel(stat) {
	return (FW.STAT_NAMES && FW.STAT_NAMES[stat]) || stat;
}

/** "ENCHANTED_SUGAR_CANE" to "Enchanted Sugar Cane"; the library has no item name table. */
function itemName(id) {
	return String(id)
		.toLowerCase()
		.split(/[_:]/)
		.map((word) => (word ? word[0].toUpperCase() + word.slice(1) : word))
		.join(' ');
}

function costText(cost) {
	if (!cost) return '';
	const parts = [];
	if (cost.coins) parts.push(commas(cost.coins) + ' coins');
	if (cost.copper) parts.push(commas(cost.copper) + ' copper');
	if (cost.bits) parts.push(commas(cost.bits) + ' bits');
	if (cost.kernels) parts.push(commas(cost.kernels) + ' kernels');
	if (cost.sowdust) parts.push(commas(cost.sowdust) + ' sowdust');
	for (const [medal, amount] of Object.entries(cost.medals || {})) {
		if (amount) parts.push(amount + ' ' + medal + (amount === 1 ? ' medal' : ' medals'));
	}
	for (const [item, amount] of Object.entries(cost.items || {})) {
		parts.push(commas(amount) + 'x ' + itemName(item));
	}
	const apply = costText(cost.applyCost);
	if (apply) parts.push(apply);
	return parts.join(', ');
}

/**
 * cropStat is the selected crop's own fortune stat. An upgrade can change many stats (a reforge
 * also changes speed, an enchant gives fortune for every crop); only the ones that count towards
 * the selected crop are kept.
 */
function convertUpgrade(upgrade, cropStat) {
	const wanted = [Stat.FarmingFortune, cropStat, Stat.Overbloom];
	const amounts = { ...(upgrade.stats || {}) };
	if (Object.keys(amounts).length === 0 && upgrade.increase) amounts[Stat.FarmingFortune] = upgrade.increase;

	const stats = [];
	for (const stat of wanted) {
		if (stat && amounts[stat]) stats.push({ name: statLabel(stat), amount: round(amounts[stat]) });
	}
	return {
		title: upgrade.title,
		onto: (upgrade.onto && upgrade.onto.name) || '',
		// Fortune gained for the selected crop, which is what the list is sorted by.
		increase: round(num(amounts[Stat.FarmingFortune]) + num(cropStat && amounts[cropStat])),
		stats,
		action: upgrade.action || '',
		category: upgrade.category || '',
		cost: costText(upgrade.cost),
		api: upgrade.api !== false,
		repeatable: upgrade.repeatable || 0,
	};
}

function convertProgress(progress) {
	const inactive = !!progress.active && progress.active.active === false;
	const stats = [];
	for (const [stat, value] of Object.entries(progress.stats || {})) {
		let current = num(value.current);
		let ratio = num(value.ratio);
		if (inactive) {
			// The library reports 0 for a source that is switched off; show what it would give.
			const shown = (progress.active.stats && progress.active.stats[stat]) ?? progress.active.fortune ?? 0;
			current = num(shown);
			ratio = current > 0 && value.max ? Math.min(current / value.max, 1) : 0;
		}
		stats.push({ name: statLabel(stat), current: round(current), max: round(value.max), ratio });
	}
	return {
		name: (progress.item && progress.item.name) || progress.name,
		source: progress.name,
		current: round(progress.current),
		max: round(progress.max),
		ratio: num(progress.ratio),
		stats,
		api: progress.api !== false,
		active: !inactive,
		note: (progress.active && progress.active.reason) || '',
		children: (progress.progress || []).map(convertProgress),
	};
}

/** Rows the website hides: nothing to show and nothing to upgrade. */
function isVisible(progress) {
	const shows = (p) => p.stats || p.nextInfo || p.maxInfo || p.item || (p.upgrades && p.upgrades.length);
	return !!(shows(progress) || (progress.progress || []).some(shows));
}

function convertBreakdown(breakdown, onlyStat) {
	const rows = [];
	for (const [name, entry] of Object.entries(breakdown || {})) {
		const value = typeof entry === 'number' ? entry : num(entry && entry.value);
		const stat = entry && typeof entry === 'object' ? entry.stat : undefined;
		if (onlyStat && stat !== onlyStat) continue;
		if (!value) continue;
		rows.push({ name, value: round(value), stat: stat ? statLabel(stat) : '' });
	}
	return rows.sort((a, b) => b.value - a.value);
}

function effectValue(effect) {
	if (effect.valueDisplay === 'none') return '';
	const amount = String(round(effect.amount));
	if (effect.valueDisplay === 'stat') return amount;
	if (effect.valueDisplay === 'percent' || effect.op === 'add-rare-pct') return '+' + amount + '%';
	return amount + 'x';
}

function convertRates(player, crop, blocksPerSecond) {
	const blocks = 72000 * (blocksPerSecond / 20);
	const info = player.getRates(crop, blocks, blocksPerSecond);
	const list = (map) =>
		Object.entries(map || {})
			.map(([name, value]) => ({ name, value: Math.round(num(value)) }))
			.filter((row) => row.value !== 0)
			.sort((a, b) => b.value - a.value);

	const effects = {};
	for (const applied of Object.values(info.appliedEffects || {})) {
		for (const effect of applied) {
			if (effect.op === 'add-drop') continue;
			const key = [effect.source, effect.op, effect.amount, effect.description || ''].join('|');
			effects[key] = { source: effect.source, value: effectValue(effect), detail: effect.description || '' };
		}
	}

	const collection = {};
	collection[Crop.Mushroom] = (info.otherCollection || {})[Crop.Mushroom];
	collection[crop] = info.collection;
	const weight = FW.createFarmingWeightCalculator({ collection }).getWeightInfo().cropWeight;

	return {
		blocksPerSecond,
		npcCoins: Math.round(num(info.npcCoins)),
		coinSources: list(info.coinSources),
		collection: Math.round(num(info.collection)),
		otherCollection: list(info.otherCollection),
		weight: round(weight),
		effects: Object.values(effects),
	};
}

function view(crop, settingsJson) {
	if (!session) throw new Error('No profile loaded');
	const settings = { ...DEFAULT_SETTINGS, ...(settingsJson ? JSON.parse(settingsJson) : {}) };
	const blocksPerSecond = settings.blocksPerSecond || 20;
	delete settings.blocksPerSecond;

	// Built fresh for each view: the library changes these objects as tools and pets are selected.
	const tools = FW.FarmingTool.fromArray(session.items);
	const pets = FW.FarmingPet.fromArray(session.pets);
	const armor = new FW.ArmorSet(FW.FarmingArmor.fromArray(session.items), FW.LotusGear.fromArray(session.items));

	const player = FW.createFarmingPlayer({
		...session.base,
		...settings,
		tools,
		pets,
		armor,
		selectedCrop: crop,
		jacobContest: { enabled: false, crop },
	});

	const pet = player.pets.slice().sort((a, b) => b.fortune - a.fortune)[0];
	if (pet) player.selectPet(pet);
	const tool = player.getBestTool(crop);
	if (tool) player.selectTool(tool);

	const cropStat = FW.CROP_INFO[crop] ? FW.CROP_INFO[crop].fortuneType : undefined;
	const cropFortune = player.getCropFortune(crop);
	const convertAll = (list) => (list || []).filter(isVisible).map(convertProgress);

	const gear = [];
	for (const [slot, piece] of Object.entries(player.armorSet.slots || {})) {
		if (piece && piece.item) gear.push({ slot, name: piece.item.name || slot, fortune: round(piece.fortune) });
	}

	const selectedPet = player.selectedPet;
	const upgrades = []
		.concat(player.getUpgrades({ stats: FORTUNE_STATS }))
		.concat((selectedPet && selectedPet.getUpgrades({ stats: FORTUNE_STATS }, player)) || [])
		.concat(player.getCropUpgrades(crop))
		.map((upgrade) => convertUpgrade(upgrade, cropStat))
		.filter((upgrade) => upgrade.stats.length > 0)
		.sort((a, b) => b.increase - a.increase);

	return JSON.stringify({
		crop,
		cropName: FW.getCropDisplayName(crop),
		cropStatName: cropStat ? statLabel(cropStat) : '',
		fortune: round(cropFortune.fortune),
		breakdown: convertBreakdown(cropFortune.breakdown),
		cropBreakdown: cropStat ? convertBreakdown(cropFortune.breakdown, cropStat) : [],
		gardenLevel: num(player.options.gardenLevel),
		plots: num(player.options.plotsUnlocked ?? (player.options.plots || []).length),
		farmingLevel: num(player.options.farmingLevel),
		milestone: num((player.options.milestones || {})[crop]),
		cropUpgrade: num((player.options.cropUpgrades || {})[crop]),
		tool: tool ? { name: tool.item.name || tool.name, fortune: round(tool.fortune) } : null,
		pet: selectedPet
			? {
					name: selectedPet.info.name,
					rarity: String(selectedPet.rarity || ''),
					level: num(selectedPet.level),
					fortune: round(selectedPet.fortune),
				}
			: null,
		gear,
		cropProgress: convertAll(player.getCropProgress(crop, [Stat.FarmingFortune, cropStat || Stat.FarmingFortune])),
		gearProgress: convertAll(player.armorSet.getProgress(FORTUNE_STATS)),
		generalProgress: convertAll(player.getProgress(FORTUNE_STATS)),
		petProgress: convertAll(player.getPetProgress(FORTUNE_STATS)),
		rates: convertRates(player, crop, blocksPerSecond),
		upgrades,
	});
}

globalThis.Sentils = { load, view };
