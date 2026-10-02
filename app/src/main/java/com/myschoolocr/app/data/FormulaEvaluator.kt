package com.myschoolocr.app.data

/**
 * Évaluateur d'expressions arithmétiques simples pour les colonnes calculées
 * (ex: D = A+B-C/10, où A/B/C désignent d'autres colonnes de la même grille).
 * Supporte +, -, *, /, parenthèses, et des variables lettres (A-Z).
 * Volontairement fait main (pas de dépendance externe, pas d'eval système sur Android).
 */
object FormulaEvaluator {

    /** Résultat du calcul, ou null si la formule est invalide, incomplète, ou division par zéro. */
    fun evaluate(formula: String, variables: Map<Char, Double>): Double? {
        val cleaned = formula.replace(" ", "")
        if (cleaned.isBlank()) return null
        return try {
            val parser = Parser(cleaned, variables)
            val result = parser.parseExpression()
            if (!parser.isAtEnd()) null else result
        } catch (e: Exception) {
            null
        }
    }

    /** Vérifie qu'une formule est syntaxiquement valide en l'évaluant avec des valeurs de test. */
    fun isSyntaxValid(formula: String, availableLetters: Set<Char>): Boolean {
        val used = formula.filter { it.isLetter() }.map { it.uppercaseChar() }.toSet()
        if (used.any { it !in availableLetters }) return false
        val dummyVariables = availableLetters.associateWith { 1.0 }
        return evaluate(formula, dummyVariables) != null
    }

    private class Parser(private val text: String, private val variables: Map<Char, Double>) {
        private var pos = 0

        fun isAtEnd() = pos >= text.length

        fun parseExpression(): Double {
            var value = parseTerm()
            while (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                val op = next()
                val rhs = parseTerm()
                value = if (op == '+') value + rhs else value - rhs
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (!isAtEnd() && (peek() == '*' || peek() == '/')) {
                val op = next()
                val rhs = parseFactor()
                value = if (op == '*') value * rhs else {
                    if (rhs == 0.0) throw ArithmeticException("Division par zéro")
                    value / rhs
                }
            }
            return value
        }

        private fun parseFactor(): Double {
            if (!isAtEnd() && peek() == '-') {
                next()
                return -parseFactor()
            }
            if (!isAtEnd() && peek() == '(') {
                next()
                val value = parseExpression()
                if (isAtEnd() || peek() != ')') throw IllegalArgumentException("Parenthèse manquante")
                next()
                return value
            }
            if (!isAtEnd() && (peek().isDigit() || peek() == '.')) {
                val start = pos
                while (!isAtEnd() && (peek().isDigit() || peek() == '.')) next()
                return text.substring(start, pos).toDouble()
            }
            if (!isAtEnd() && peek().isLetter()) {
                val letter = next().uppercaseChar()
                return variables[letter] ?: throw NoSuchElementException("Variable $letter manquante")
            }
            throw IllegalArgumentException("Formule invalide")
        }

        private fun peek(): Char = text[pos]
        private fun next(): Char = text[pos++]
    }
}
