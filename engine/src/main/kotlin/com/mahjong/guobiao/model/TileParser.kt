package com.mahjong.guobiao.model

/**
 * 牌型字符串解析。记法：
 *  - "123m" -> 1m,2m,3m；"456p" -> 4p,5p,6p；"789s" -> 7s,8s,9s
 *  - "东南西北中发白" 对应风箭牌
 *  - "春夏秋冬梅兰竹菊" 对应花牌
 *  - 可拼接：如 "123m456p789s东南东"
 *
 * 花牌用首字识别：春/夏/秋/冬/梅/兰/竹/菊。
 */
object TileParser {

    private val FLOWER_CHARS = mapOf(
        '春' to 34, '夏' to 35, '秋' to 36, '冬' to 37,
        '梅' to 38, '兰' to 39, '竹' to 40, '菊' to 41
    )
    private val HONOR_CHARS = mapOf(
        '东' to 27, '南' to 28, '西' to 29, '北' to 30,
        '中' to 31, '发' to 32, '白' to 33
    )
    private val SUIT_CHARS = mapOf('m' to 0, 'p' to 9, 's' to 18)

    fun parse(text: String): List<TileType> {
        val result = mutableListOf<TileType>()
        val digits = StringBuilder()
        for (ch in text) {
            when {
                ch.isWhitespace() -> continue
                ch in '1'..'9' -> digits.append(ch)
                ch in SUIT_CHARS -> {
                    val base = SUIT_CHARS.getValue(ch)
                    require(digits.isNotEmpty()) { "花色 '$ch' 前无数字: $text" }
                    for (d in digits) result.add(TileType(base + (d - '0') - 1))
                    digits.clear()
                }
                ch in HONOR_CHARS -> {
                    require(digits.isEmpty()) { "字牌 '$ch' 前不应有数字: $text" }
                    result.add(TileType(HONOR_CHARS.getValue(ch)))
                }
                ch in FLOWER_CHARS -> {
                    require(digits.isEmpty()) { "花牌 '$ch' 前不应有数字: $text" }
                    result.add(TileType(FLOWER_CHARS.getValue(ch)))
                }
                else -> throw IllegalArgumentException("无法解析字符 '$ch' in: $text")
            }
        }
        require(digits.isEmpty()) { "尾部数字未匹配花色: $text" }
        return result
    }

    /** 解析并构造暗手（无副露无花）。 */
    fun parseHand(text: String): Hand {
        val tiles = parse(text)
        val flowers = tiles.filter { it.isFlower }
        val concealed = tiles.filter { !it.isFlower }
        return Hand(concealed, emptyList(), flowers)
    }

    fun toString(tiles: List<TileType>): String = tiles.sorted().joinToString("") { it.toString() }
}
