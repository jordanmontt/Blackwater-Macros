package com.blackwatermacros.app.core.foodquestions

import com.blackwatermacros.app.core.foodquestions.analyses.Compare
import com.blackwatermacros.app.core.foodquestions.analyses.DayLog
import com.blackwatermacros.app.core.foodquestions.analyses.FoodStats
import com.blackwatermacros.app.core.foodquestions.analyses.FoodVsWeight
import com.blackwatermacros.app.core.foodquestions.analyses.Ranking

/**
 * Food questions (web `food-questions/index.ts`): the coach answers questions
 * about the user's own food log in three steps. (1) The model turns the question
 * into a request ([buildFoodRequestPrompt] → [parseFoodRequest]); (2) the app
 * computes the numbers from the log ([answerFoodRequest]); (3) the model explains
 * the result, which travels with the question ([foodQuestionMessage]).
 *
 * Adding an analysis: its texts in food-questions.json, an [AnalysisId], one
 * file in analyses/, one line in [ANALYSES] (docs/TECHNICAL.md §8.7).
 */
val ANALYSES: Map<AnalysisId, Analysis> = listOf(FoodStats, Ranking, DayLog, Compare, FoodVsWeight).associateBy { it.id }

/**
 * What the app answers to a request: numbers for the model to explain, or a
 * reply of its own. Null when the request cannot be answered as asked (or is
 * «none»); the coach then says it did not understand, or answers as usual.
 */
fun answerFoodRequest(log: FoodLog, request: FoodRequest): FoodAnswer? {
    val analysis = request.analysis ?: return null
    return ANALYSES.getValue(analysis).answer(PreparedLog(log), request)
}

/** What the model reads in step 3: the user's words, then the app's numbers. */
fun foodQuestionMessage(question: String, resultText: String): String = "$question\n\n$resultText"
