package com.baijum.purandara.domain.varisai.catalog

/**
 * Vakra Janta Varisai 1–2, in Mayamalavagowla. Traditional exercises.
 *
 * Copied verbatim from the `varisai` blocks of the author's Mayamalavagowla
 * practice workbook so it can be compared line by line. Notation grammar:
 * docs/plan.md, section 3.2.
 *
 * Vakra Janta 3 is omitted: its lines have 10 aksharas and its comma
 * timing is unconfirmed (see docs/plan.md, section 3.4).
 */
internal val VAKRA_JANTA_VARISAI_NOTATION: String = """
1
SS MM GG RR | SS RR | GG MM
RR PP MM GG | RR GG | MM PP
GG DD PP MM | GG MM | PP DD
MM NN DD PP | MM PP | DD NN
PP S′S′ NN DD | PP DD | NN S′S′
S′S′ PP DD NN | S′S′ NN | DD PP
NN MM PP DD | NN DD | PP MM
DD GG MM PP | DD PP | MM GG
PP RR GG MM | PP MM | GG RR
MM SS RR GG | MM GG | RR SS

2
SM GM RG SR | SS RR | GG MM
RP MP GM RG | RR GG | MM PP
GD PD MG GM | GG MM | PP DD
MN DN PD MP | MM PP | DD NN
PS′ NS′ DN PD | PP DD | NN S′S′
S′P DP ND S′N | S′S′ NN | DD PP
NM PM DP ND | NN DD | PP MM
DG MG PM DP | DD PP | MM GG
PR GR MG PM | PP MM | GG RR
MS RS GR MG | MM GG | RR SS
""".trimIndent()
