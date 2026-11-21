package vn.guildmaster.core

enum class Target { ENEMY, ENEMIES, SELF, ALLIES, ALLY_LOWEST, ALLY_DOWN }

enum class Effect { NONE, TAUNT, STUN, SHIELD, SLOW, BLESS, HEAL, REVIVE }

/** mult = hệ số ATK; value = độ lớn hiệu ứng (tỉ lệ); unlock = cấp mở. */
class Skill(
    val name: String,
    val target: Target,
    val mult: Double = 0.0,
    val cooldown: Int = 0,
    val hits: Int = 1,
    val effect: Effect = Effect.NONE,
    val chance: Double = 1.0,
    val turns: Int = 0,
    val value: Double = 0.0,
    val critBonus: Double = 0.0,
    val unlock: Int = 1,
    val desc: String = "",
)

class Species(val name: String, val hp: Int, val atk: Int, val def: Int, val spd: Int, val skill: Skill? = null)

class Zone(val name: String, val minLevel: Int, val maxLevel: Int, val unlockGuild: Int, val species: List<Species>)

object Data {
    val baseStats: Map<HeroClass, IntArray> = mapOf(
        // HP, ATK, DEF, SPD, Crit%, CritDMG%
        HeroClass.WARRIOR to intArrayOf(220, 18, 16, 95, 5, 150),
        HeroClass.MAGE to intArrayOf(120, 28, 6, 100, 8, 160),
        HeroClass.ARCHER to intArrayOf(140, 24, 8, 115, 15, 170),
        HeroClass.PRIEST to intArrayOf(140, 14, 10, 105, 5, 150),
    )

    val skills: Map<HeroClass, List<Skill>> = mapOf(
        HeroClass.WARRIOR to listOf(
            Skill("Khiêu khích", Target.SELF, cooldown = 4, effect = Effect.TAUNT, turns = 2, value = 0.30,
                desc = "Quái buộc đánh mình 2 lượt, +30% DEF 2 lượt. Hồi 4."),
            Skill("Đập khiên", Target.ENEMY, 1.4, 3, effect = Effect.STUN, chance = 0.3, turns = 1, unlock = 5,
                desc = "1.4× ATK 1 mục tiêu, 30% choáng 1 lượt. Hồi 3."),
            Skill("Tường thép", Target.ALLIES, cooldown = 6, effect = Effect.SHIELD, turns = 2, value = 0.25, unlock = 15,
                desc = "Cả đội giảm 25% sát thương nhận 2 lượt. Hồi 6."),
        ),
        HeroClass.MAGE to listOf(
            Skill("Cầu lửa", Target.ENEMIES, 0.9, 3, desc = "0.9× ATK mọi kẻ địch. Hồi 3."),
            Skill("Băng giá", Target.ENEMY, 1.2, 3, effect = Effect.SLOW, turns = 2, value = 0.30, unlock = 5,
                desc = "1.2× ATK 1 mục tiêu, −30% SPD 2 lượt. Hồi 3."),
            Skill("Thiên thạch", Target.ENEMIES, 1.8, 7, unlock = 15, desc = "1.8× ATK mọi kẻ địch. Hồi 7."),
        ),
        HeroClass.ARCHER to listOf(
            Skill("Bắn đôi", Target.ENEMY, 0.8, 2, hits = 2, desc = "2 phát 0.8× ATK. Hồi 2."),
            Skill("Ngắm bắn", Target.ENEMY, 2.2, 4, critBonus = 0.30, unlock = 5, desc = "2.2× ATK, +30% crit. Hồi 4."),
            Skill("Mưa tên", Target.ENEMIES, 0.7, 4, unlock = 15, desc = "0.7× ATK mọi kẻ địch. Hồi 4."),
        ),
        HeroClass.PRIEST to listOf(
            Skill("Hồi máu", Target.ALLY_LOWEST, 1.8, 2, effect = Effect.HEAL, value = 0.10,
                desc = "Hồi 1.8× ATK + 10% HP tối đa cho đồng đội thấp HP nhất. Hồi 2."),
            Skill("Ban phúc", Target.ALLIES, cooldown = 5, effect = Effect.BLESS, turns = 3, value = 0.20, unlock = 5,
                desc = "Cả đội +20% ATK 3 lượt. Hồi 5."),
            Skill("Hồi sinh", Target.ALLY_DOWN, effect = Effect.REVIVE, value = 0.40, unlock = 15,
                desc = "Hồi sinh 1 đồng đội với 40% HP. 1 lần/trận."),
        ),
    )

    val zones: List<Zone> = listOf(
        Zone("Rừng Sương", 1, 10, 1, listOf(
            Species("Nhầy xanh", 70, 9, 3, 85),
            Species("Sói xám", 60, 12, 3, 110, Skill("Cắn", Target.ENEMY, 1.3, 3)),
            Species("Goblin", 65, 11, 4, 100),
            Species("Pháp sư goblin", 50, 13, 2, 95, Skill("Cầu lửa", Target.ENEMIES, 0.7, 4)),
            Species("Nấm độc", 85, 8, 5, 80),
        )),
        Zone("Hang Đá Đỏ", 8, 20, 3, listOf(
            Species("Dơi lửa", 60, 13, 3, 120),
            Species("Người đá", 130, 10, 9, 75, Skill("Nện đá", Target.ENEMY, 1.5, 4)),
            Species("Thằn lằn đỏ", 80, 12, 5, 100),
            Species("Thợ mỏ hoá điên", 85, 13, 5, 95),
            Species("Linh hồn lửa", 60, 14, 3, 105, Skill("Phun lửa", Target.ENEMIES, 0.8, 4)),
        )),
        Zone("Đầm Lầy Tro", 18, 32, 6, listOf(
            Species("Cá sấu tro", 120, 13, 7, 85, Skill("Ngoạm", Target.ENEMY, 1.5, 3)),
            Species("Phù thuỷ đầm", 70, 15, 3, 100, Skill("Mưa độc", Target.ENEMIES, 0.75, 4)),
            Species("Xác khô", 100, 12, 6, 80),
            Species("Muỗi khổng lồ", 55, 14, 2, 130),
            Species("Rễ quấn", 110, 10, 8, 75),
            Species("Bóng ma tro", 75, 15, 4, 110),
        )),
        Zone("Thành Cổ Băng", 30, 50, 10, listOf(
            Species("Hiệp sĩ băng", 130, 15, 9, 90, Skill("Chém băng", Target.ENEMY, 1.5, 3)),
            Species("Sói tuyết", 85, 16, 4, 120, Skill("Cắn", Target.ENEMY, 1.3, 3)),
            Species("Pháp sư băng", 75, 17, 3, 100, Skill("Bão tuyết", Target.ENEMIES, 0.8, 4)),
            Species("Người tuyết", 150, 13, 8, 80),
            Species("Giáp rỗng", 110, 14, 10, 85),
            Species("Rồng băng non", 140, 18, 7, 100, Skill("Hơi thở băng", Target.ENEMIES, 0.9, 5)),
        )),
    )

    /** id loài = zoneIndex * 100 + vị trí trong vùng. */
    fun species(id: Int): Species = zones[id / 100].species[id % 100]

    val syllables = listOf(
        "ka", "ri", "el", "dor", "mir", "an", "ia", "lo", "ven", "sa", "gor", "fen",
        "li", "ra", "on", "ul", "vas", "ty", "bra", "nim", "tha", "lys", "or", "ke",
    )

    val durations = intArrayOf(5, 15, 30, 60, 120, 240, 480)
}
