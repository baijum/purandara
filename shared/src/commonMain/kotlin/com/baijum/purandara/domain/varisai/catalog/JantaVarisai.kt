package com.baijum.purandara.domain.varisai.catalog

/**
 * Janta Varisai 1–7, in Mayamalavagowla. Traditional exercises.
 *
 * Copied verbatim from the `varisai` blocks of the author's Mayamalavagowla
 * practice workbook so it can be compared line by line. Notation grammar:
 * docs/plan.md, section 3.2.
 *
 * Janta 8 is omitted: its avartas have 9 aksharas and its comma timing
 * is unconfirmed (see docs/plan.md, section 3.4).
 */
internal val JANTA_VARISAI_NOTATION: String = """
1
SS RR GG MM | PP DD | NN S′S′
S′S′ NN DD PP | MM GG | RR SS

2
SS RR GG MM | RR GG | MM PP
GG MM PP DD | MM PP | DD NN
PP DD NN S′S′ | S′S′ NN | DD PP
NN DD PP MM | DD PP | MM GG
PP MM GG RR | MM GG | RR SS

3
SS RR GG RR | SS RR | GG MM
RR GG MM GG | RR GG | MM PP
GG MM PP MM | GG MM | PP DD
MM PP DD PP | MM PP | DD NN
PP DD NN DD | PP DD | NN S′S′
S′S′ NN DD NN | S′S′ NN | DD PP
NN DD PP DD | NN DD | PP MM
DD PP MM PP | DD PP | MM GG
PP MM GG MM | PP MM | GG RR
MM GG RR GG | MM GG | RR SS

4
SS RR GS RG | SS RR | GG MM
RR GG MR GM | RR GG | MM PP
GG MM PG MP | GG MM | PP DD
MM PP DM PD | MM PP | DD NN
PP DD NP DN | PP DD | NN S′S′
S′S′ NN DS ND | S′S′ NN | DD PP
NN DD PN DP | NN DD | PP MM
DD PP MD PM | DD PP | MM GG
PP MM GP MG | PP MM | GG RR
MM GG RM GR | MM GG | RR SS

5
SS RS SR SR | SS RR | GG MM
RR GR RG GM | GG MM | PP DD
GG MG GM MP | MM PP | DD NN
MM PM MP PD | PP DD | NN S′S′
PP DP PD S′N | S′S′ NN | DD PP
S′S′ NS′ S′N ND | NN DD | PP MM
NN DN ND DP | DD PP | MM GG
DD PD DP PM | PP MM | GG RR
PP MP PM MG | MM GG | RR SS
MM GM MG GR | MM GG | RR SS

6
SS SR RR GG | SS RR | GG MM
RR RG GG MM | RR GG | MM PP
GG GM MM PP | GG MM | PP DD
MM MP PP DD | MM PP | DD NN
PP PD DD NN | PP DD | NN S′S′
S′S′ S′N NN DD | S′S′ NN | DD PP
NN ND DD PP | NN DD | PP MM
DD DP PP MM | DD PP | MM GG
PP PM MM GG | PP MM | GG RR
MM MG GG RR | MM GG | RR SS

7
S , S R | , R | G G || S S R R | G G M M
R , R G | , G | M M || R R G G | M M P P
G , G M | , M | P P || G G M M | P P D D
M , M P | , P | D D || M M P P | D D N N
P , P D | , D | N N || P P D D | N N S′ S′
S′ , S′ N | , N | D D || S′ S′ N N | D D P P
N , N D | , D | P P || N N D D | P P M M
D , D P | , P | M M || D D P P | M M G G
P , P M | , M | G G || P P M M | G G R R
M , M G | , G | R R || M M G G | R R S S
""".trimIndent()
