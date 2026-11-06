package com.baijum.purandara.domain.varisai.catalog

/**
 * The seven sapta tala alankarams, in Mayamalavagowla. Traditional exercises.
 *
 * Source: karnatik.com, Beginners' Lesson 6 (https://karnatik.com/lesson6.shtml,
 * by Rani), copied verbatim except for the notation conventions: the site's
 * lowercase letters (middle octave) are written as capitals here, and its
 * capital `S` (upper octave) as `S′`. The site's eighth alankaram (Sankeerna
 * jati Eka) is left out: only the traditional seven are taught here. Each line
 * is one avarta; the jatis follow the site and docs/plan-alankaram-gitam.md:
 * Chaturasra Dhruva, Matya, Rupaka (drutam first) and Eka, Misra Jhampa, Tisra
 * Triputa and Khanda Ata. Notation grammar: docs/plan.md, section 3.2.
 */
internal val ALANKARAM_NOTATION: String = """
1
tala: dhruva-4
S R G M | G R | S R G R | S R G M ||
R G M P | M G | R G M G | R G M P ||
G M P D | P M | G M P M | G M P D ||
M P D N | D P | M P D P | M P D N ||
P D N S′ | N D | P D N D | P D N S′ ||
S′ N D P | D N | S′ N D N | S′ N D P ||
N D P M | P D | N D P D | N D P M ||
D P M G | M P | D P M P | D P M G ||
P M G R | G M | P M G M | P M G R ||
M G R S | R G | M G R G | M G R S ||

2
tala: matya-4
S R G R | S R | S R G M ||
R G M G | R G | R G M P ||
G M P M | G M | G M P D ||
M P D P | M P | M P D N ||
P D N D | P D | P D N S′ ||
S′ N D N | S′ N | S′ N D P ||
N D P D | N D | N D P M ||
D P M P | D P | D P M G ||
P M G M | P M | P M G R ||
M G R G | M G | M G R S ||

3
tala: rupaka-4
S R | S R G M ||
R G | R G M P ||
G M | G M P D ||
M P | M P D N ||
P D | P D N S′ ||
S′ N | S′ N D P ||
N D | N D P M ||
D P | D P M G ||
P M | P M G R ||
M G | M G R S ||

4
tala: jhampa-7
S R G S R S R | G | M , ||
R G M R G R G | M | P , ||
G M P G M G M | P | D , ||
M P D M P M P | D | N , ||
P D N P D P D | N | S′ , ||
S′ N D S′ N S′ N | D | P , ||
N D P N D N D | P | M , ||
D P M D P D P | M | G , ||
P M G P M P M | G | R , ||
M G R M G M G | R | S , ||

5
tala: triputa-3
S R G | S R | G M ||
R G M | R G | M P ||
G M P | G M | P D ||
M P D | M P | D N ||
P D N | P D | N S′ ||
S′ N D | S′ N | D P ||
N D P | N D | P M ||
D P M | D P | M G ||
P M G | P M | G R ||
M G R | M G | R S ||

6
tala: ata-5
S R , G , | S , R G , | M , | M , ||
R G , M , | R , G M , | P , | P , ||
G M , P , | G , M P , | D , | D , ||
M P , D , | M , P D , | N , | N , ||
P D , N , | P , D N , | S′ , | S′ , ||
S′ N , D , | S′ , N D , | P , | P , ||
N D , P , | N , D P , | M , | M , ||
D P , M , | D , P M , | G , | G , ||
P M , G , | P , M G , | R , | R , ||
M G , R , | M , G R , | S , | S , ||

7
tala: eka-4
S R G M ||
R G M P ||
G M P D ||
M P D N ||
P D N S′ ||
S′ N D P ||
N D P M ||
D P M G ||
P M G R ||
M G R S ||
"""
