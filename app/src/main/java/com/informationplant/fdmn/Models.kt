package com.informationplant.fdmn

import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val createdAt: String,
    val lastLogin: String,
) {
    val isAdmin get() = role == "admin"

    fun toJson(): JSONObject = jo(
        "id" to id, "name" to name, "email" to email, "role" to role, "status" to status,
        "created_at" to createdAt, "last_login" to lastLogin,
    )

    companion object {
        fun from(o: JSONObject) = User(
            o.optInt("id"), o.str("name"), o.str("email"), o.str("role"), o.str("status"),
            o.str("created_at"), o.str("last_login"),
        )
    }
}

data class Person(
    val id: Int = 0,
    val sl: Int = 0,
    val name: String = "",
    val sex: String = "",
    val fcn: String = "",
    val dob: String = "",
    val relation: String = "",
    val mainBlock: String = "",
    val subBlock: String = "",
    val majhi: String = "",
    val remarks: String = "",
    val updatedAt: String = "",
    val updatedBy: String = "",
) {
    val dobText get() = DateUtil.display(dob)
    val ageText get() = DateUtil.age(dob)
    val sexText get() = when (sex) { "M" -> "Male"; "F" -> "Female"; else -> sex }
    val blockText get() = listOf(mainBlock, subBlock).filter { it.isNotBlank() }.joinToString(" / ")

    fun toJson(): JSONObject = jo(
        "id" to id, "name" to name, "sex" to sex, "fcn" to fcn, "dob" to dob, "relation" to relation,
        "main_block" to mainBlock, "sub_block" to subBlock, "majhi" to majhi, "remarks" to remarks,
    )

    companion object {
        fun from(o: JSONObject) = Person(
            id = o.optInt("id"), sl = o.optInt("sl"), name = o.str("name"), sex = o.str("sex"),
            fcn = o.str("fcn"), dob = o.str("dob"), relation = o.str("relation"),
            mainBlock = o.str("main_block"), subBlock = o.str("sub_block"), majhi = o.str("majhi"),
            remarks = o.str("remarks"), updatedAt = o.str("updated_at"), updatedBy = o.str("updated_by_name"),
        )
    }
}

data class SubBlock(val name: String, val count: Int, val families: Int)
data class MainBlock(val name: String, val count: Int, val families: Int, val subs: List<SubBlock>)

data class Stats(
    val total: Int = 0, val families: Int = 0, val male: Int = 0, val female: Int = 0,
    val pendingUsers: Int = 0, val openMessages: Int = 0, val unreadReplies: Int = 0,
    val under5: Int = 0, val age6to12: Int = 0, val age13to17: Int = 0,
) {
    companion object {
        fun from(o: JSONObject) = Stats(
            o.optInt("total"), o.optInt("families"), o.optInt("male"), o.optInt("female"),
            o.optInt("pending_users"), o.optInt("open_messages"), o.optInt("unread_replies"),
            o.optInt("under5"), o.optInt("age6to12"), o.optInt("age13to17"),
        )
    }
}

data class Meta(
    val blocks: List<MainBlock> = emptyList(),
    val relations: List<String> = emptyList(),
    val majhis: List<String> = emptyList(),
) {
    val mainNames get() = blocks.map { it.name }.filter { it.isNotBlank() }
    fun subNames(main: String): List<String> =
        (if (main.isBlank()) blocks else blocks.filter { it.name == main })
            .flatMap { b -> b.subs.map { it.name } }
            .filter { it.isNotBlank() }
            .distinct()
}

data class Message(
    val id: Int, val userName: String, val userEmail: String, val beneficiaryId: Int, val beneficiary: String,
    val subject: String, val body: String, val reply: String, val status: String,
    val createdAt: String, val repliedAt: String, val repliedBy: String, val userSeen: Boolean,
) {
    companion object {
        fun from(o: JSONObject) = Message(
            o.optInt("id"), o.str("user_name"), o.str("user_email"), o.optInt("beneficiary_id"), o.str("beneficiary"),
            o.str("subject"), o.str("body"), o.str("reply"), o.str("status"),
            o.str("created_at"), o.str("replied_at"), o.str("replied_by"), o.optInt("user_seen", 1) == 1,
        )
    }
}

data class Filters(
    val q: String = "",
    val main: String = "",
    val sub: String = "",
    val sex: String = "",
    val relation: String = "",
    val majhi: String = "",
    val age: String = "",
) {
    val active get() = q.isNotBlank() || main.isNotBlank() || sub.isNotBlank() || sex.isNotBlank() || relation.isNotBlank() || majhi.isNotBlank() || age.isNotBlank()

    fun toJson(): JSONObject = jo(
        "q" to q, "main" to main, "sub" to sub, "sex" to sex, "relation" to relation, "majhi" to majhi, "age" to age,
    )

    fun describe(): String {
        val parts = mutableListOf<String>()
        if (main.isNotBlank()) parts += "Main Block $main"
        if (sub.isNotBlank()) parts += "Sub-Block $sub"
        if (sex.isNotBlank()) parts += if (sex == "M") "Male" else if (sex == "F") "Female" else sex
        if (relation.isNotBlank()) parts += "Relation $relation"
        if (age.isNotBlank()) parts += "Children " + ageLabel(age)
        if (majhi.isNotBlank()) parts += "Majhi $majhi"
        if (q.isNotBlank()) parts += "Search \"$q\""
        return if (parts.isEmpty()) "All records" else parts.joinToString(" · ")
    }
}

val AGE_GROUPS = listOf("u5" to "0–5 yrs", "c6" to "6–12 yrs", "c13" to "13–17 yrs")
fun ageLabel(code: String): String = AGE_GROUPS.firstOrNull { it.first == code }?.second ?: code

fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)

object DateUtil {
    private val displayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private val patterns = listOf(
        "d MMM yyyy", "d-MMM-yyyy", "d/MMM/yyyy", "d.MMM.yyyy", "d MMMM yyyy", "d-MMMM-yyyy",
        "MMM d, yyyy", "MMMM d, yyyy", "MMM d yyyy",
        "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "yyyy-M-d", "yyyy/M/d", "yyyy.M.d",
        "d MMM yy", "d-MMM-yy", "d/M/yy", "d-M-yy",
    )
    private val formatters = patterns.map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH)
    }

    fun parseIso(iso: String): LocalDate? =
        try { if (iso.length >= 10) LocalDate.parse(iso.substring(0, 10)) else null } catch (e: Exception) { null }

    fun display(iso: String): String = parseIso(iso)?.format(displayFmt) ?: ""

    fun age(iso: String): String {
        val d = parseIso(iso) ?: return ""
        val p = Period.between(d, LocalDate.now())
        if (p.isNegative) return ""
        return when {
            p.years >= 1 -> "${p.years} yrs"
            p.months >= 1 -> "${p.months} mo"
            else -> "${p.days} days"
        }
    }

    private fun okYear(d: LocalDate) = d.year in 1880..(LocalDate.now().year + 1)

    /** Reads many common date styles. Returns ISO (yyyy-MM-dd) or null. */
    fun parseFlexible(raw: String): String? {
        val s = raw.trim().replace(Regex("\\s+"), " ").removeSuffix(".")
        if (s.isEmpty()) return null
        if (Regex("^\\d{4}$").matches(s)) {
            val y = s.toInt()
            return if (y in 1880..LocalDate.now().year) "$y-01-01" else null
        }
        for (f in formatters) {
            try {
                var d = LocalDate.parse(s, f)
                if (d.year > LocalDate.now().year + 1) d = d.minusYears(100)
                if (okYear(d)) return d.toString()
            } catch (_: Exception) { }
        }
        return null
    }

    /** Excel stores dates as day numbers counted from 30 Dec 1899. */
    fun fromExcelSerial(serial: Double): String? {
        if (serial < 1 || serial > 80000) return null
        val d = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        return if (okYear(d)) d.toString() else null
    }
}
