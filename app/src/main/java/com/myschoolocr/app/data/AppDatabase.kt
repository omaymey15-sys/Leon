package com.myschoolocr.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Database(
    entities = [
        GridEntity::class, StudentEntity::class,
        GridColumnEntity::class, StudentScoreEntity::class,
        ClassEntity::class, ClassStudentEntity::class,
        PendingScanEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gridDao(): GridDao
    abstract fun studentDao(): StudentDao
    abstract fun gridColumnDao(): GridColumnDao
    abstract fun studentScoreDao(): StudentScoreDao
    abstract fun classDao(): ClassDao
    abstract fun classStudentDao(): ClassStudentDao
    abstract fun pendingScanDao(): PendingScanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myschool_ocr.db"
                )
                    // Projet en développement actif : pas de migration formelle pour l'instant.
                    // Si le schéma change, la base est recréée (les données de test sont perdues).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

data class DashboardStats(
    val totalGrids: Int,
    val totalStudents: Int,
    val totalStudentsWithAtLeastOneScore: Int,
    val averageOn20: Double?
)

/** Vue "table" d'une grille : ses colonnes +, pour chaque élève, ses notes/total/mention. */
data class GridTableRow(
    val student: StudentEntity,
    val scoresByColumnId: Map<Long, StudentScoreEntity>
) {
    fun totalObtained(columns: List<GridColumnEntity>): Double =
        columns.sumOf { scoresByColumnId[it.id]?.value ?: 0.0 }

    fun hasAnyScore(): Boolean = scoresByColumnId.isNotEmpty()

    fun isComplete(columns: List<GridColumnEntity>): Boolean =
        columns.isNotEmpty() && columns.all { scoresByColumnId.containsKey(it.id) }
}

/**
 * Repository unique : point d'accès à toutes les données locales.
 * Tout est en local (Room / SQLite) => l'appli reste 100% fonctionnelle hors-ligne.
 */
class Repository(private val db: AppDatabase) {

    /**
     * Réinitialise complètement l'application : supprime toutes les grilles, colonnes,
     * élèves, notes, classes et scans en attente (vide toutes les tables, conserve le
     * schéma). Les réglages (Réglages > Apparence, langue...) ne sont pas concernés,
     * ils sont stockés séparément (DataStore, voir SettingsRepository).
     */
    suspend fun wipeAllData() {
        db.clearAllTables()
    }

    /**
     * Sauvegarde complète de toutes les données de l'app (grilles, colonnes, élèves, notes,
     * classes) au format JSON. Ne contient aucune photo (déjà exclues par design — voir
     * ScanPhotoStorage). Sert de filet de sécurité en cas de changement de téléphone ;
     * la ré-importation n'est pas implémentée pour l'instant, ce fichier est à visée de
     * sauvegarde/consultation, pas de restauration automatique.
     */
    suspend fun exportAllDataAsJson(): String {
        val root = org.json.JSONObject()
        root.put("exportedAt", System.currentTimeMillis())

        val gridsArray = org.json.JSONArray()
        for (grid in getCurrentGrids()) {
            val gridJson = org.json.JSONObject()
            gridJson.put("id", grid.id)
            gridJson.put("name", grid.name)
            gridJson.put("subject", grid.subject)
            gridJson.put("createdAt", grid.createdAt)

            val columnsArray = org.json.JSONArray()
            for (col in db.gridColumnDao().getByGridOnce(grid.id)) {
                val colJson = org.json.JSONObject()
                colJson.put("id", col.id)
                colJson.put("name", col.name)
                colJson.put("maxPoints", col.maxPoints)
                colJson.put("position", col.position)
                colJson.put("formula", col.formula ?: org.json.JSONObject.NULL)
                columnsArray.put(colJson)
            }
            gridJson.put("columns", columnsArray)

            val studentsArray = org.json.JSONArray()
            val scores = db.studentScoreDao().getByGridOnce(grid.id).groupBy { it.studentId }
            for (student in db.studentDao().getByGridOnce(grid.id)) {
                val studentJson = org.json.JSONObject()
                studentJson.put("id", student.id)
                studentJson.put("name", student.name)
                val scoresArray = org.json.JSONArray()
                for (score in scores[student.id].orEmpty()) {
                    val scoreJson = org.json.JSONObject()
                    scoreJson.put("columnId", score.columnId)
                    scoreJson.put("value", score.value)
                    scoreJson.put("ocrUnconfirmed", score.ocrUnconfirmed)
                    scoresArray.put(scoreJson)
                }
                studentJson.put("scores", scoresArray)
                studentsArray.put(studentJson)
            }
            gridJson.put("students", studentsArray)
            gridsArray.put(gridJson)
        }
        root.put("grids", gridsArray)

        val classesArray = org.json.JSONArray()
        for (cls in getCurrentClasses()) {
            val classJson = org.json.JSONObject()
            classJson.put("id", cls.id)
            classJson.put("name", cls.name)
            val studentsArray = org.json.JSONArray()
            for (student in db.classStudentDao().getByClassOnce(cls.id)) {
                studentsArray.put(student.name)
            }
            classJson.put("students", studentsArray)
            classesArray.put(classJson)
        }
        root.put("classes", classesArray)

        return root.toString(2)
    }

    private suspend fun getCurrentGrids(): List<GridEntity> = db.gridDao().getAllOnce()
    private suspend fun getCurrentClasses(): List<ClassEntity> = db.classDao().getAllOnce()

    // -------------------- Grilles de cotation --------------------

    val grids = db.gridDao().observeAll()

    /** Crée une grille avec ses colonnes de notation et sa liste d'élèves initiale. */
    suspend fun createGrid(
        name: String,
        subject: String,
        columns: List<EditableColumnRow>,
        studentNames: List<String>,
        className: String = "",
        professorName: String = "",
        gridDate: String = "",
        term: String = ""
    ): Long {
        val gridId = db.gridDao().insert(
            GridEntity(name = name, subject = subject, className = className, professorName = professorName, gridDate = gridDate, term = term)
        )

        columns.filter { it.name.isNotBlank() }.forEachIndexed { index, col ->
            db.gridColumnDao().insert(GridColumnEntity(gridId = gridId, name = col.name, maxPoints = col.maxPoints, position = index, formula = col.formula))
        }

        val cleanedNames = studentNames.filter { it.isNotBlank() }
        if (cleanedNames.isNotEmpty()) {
            db.studentDao().insertAll(cleanedNames.map { StudentEntity(gridId = gridId, name = it) })
        }
        return gridId
    }

    suspend fun getGrid(gridId: Long) = db.gridDao().getById(gridId)

    fun observeStudents(gridId: Long) = db.studentDao().observeByGrid(gridId)

    suspend fun getStudentsOnce(gridId: Long) = db.studentDao().getByGridOnce(gridId)

    suspend fun deleteGrid(grid: GridEntity) = db.gridDao().delete(grid)

    fun observeColumns(gridId: Long) = db.gridColumnDao().observeByGrid(gridId)

    suspend fun getColumnsOnce(gridId: Long) = db.gridColumnDao().getByGridOnce(gridId)

    fun observeScores(gridId: Long) = db.studentScoreDao().observeByGrid(gridId)

    /**
     * Combine élèves + colonnes + notes en lignes de tableau prêtes à afficher,
     * dans l'esprit du document papier (une ligne par élève, une colonne par évaluation).
     */
    fun observeGridTable(gridId: Long): Flow<List<GridTableRow>> =
        combine(observeStudents(gridId), observeScores(gridId)) { students, scores ->
            val scoresByStudent = scores.groupBy { it.studentId }
            students.map { student ->
                GridTableRow(
                    student = student,
                    scoresByColumnId = scoresByStudent[student.id].orEmpty().associateBy { it.columnId }
                )
            }
        }

    /** Enregistre/écrase la note d'un élève pour une colonne donnée. */
    suspend fun setScore(studentId: Long, columnId: Long, value: Double, confirmedByUser: Boolean) {
        db.studentScoreDao().upsert(
            StudentScoreEntity(studentId = studentId, columnId = columnId, value = value, ocrUnconfirmed = !confirmedByUser)
        )
    }

    /**
     * Met à jour les infos de la grille + synchronise sa liste d'élèves et ses colonnes de notation.
     * Renommer un élève ou une colonne garde son historique ; les retirer supprime leurs notes.
     */
    suspend fun updateGridWithStudentsAndColumns(
        grid: GridEntity,
        studentRows: List<EditableStudentRow>,
        columnRows: List<EditableColumnRow>
    ) {
        db.gridDao().update(grid)

        val existingStudents = db.studentDao().getByGridOnce(grid.id)
        syncEditableRows(
            existing = existingStudents,
            idOf = { it.id },
            nameOf = { it.name },
            rows = studentRows,
            onDelete = { db.studentDao().delete(it) },
            onRename = { student, newName -> db.studentDao().update(student.copy(name = newName)) },
            onInsertNew = { newName -> db.studentDao().insert(StudentEntity(gridId = grid.id, name = newName)) }
        )

        val existingColumns = db.gridColumnDao().getByGridOnce(grid.id)
        val keptColumnIds = columnRows.mapNotNull { it.id }.toSet()
        existingColumns.filter { it.id !in keptColumnIds }.forEach { db.gridColumnDao().delete(it) }
        columnRows.forEachIndexed { index, row ->
            if (row.id != null) {
                val existing = existingColumns.find { it.id == row.id }
                if (existing != null && (existing.name != row.name || existing.maxPoints != row.maxPoints || existing.position != index || existing.formula != row.formula)) {
                    db.gridColumnDao().update(existing.copy(name = row.name, maxPoints = row.maxPoints, position = index, formula = row.formula))
                }
            } else if (row.name.isNotBlank()) {
                db.gridColumnDao().insert(GridColumnEntity(gridId = grid.id, name = row.name, maxPoints = row.maxPoints, position = index, formula = row.formula))
            }
        }
    }

    /**
     * Calcule automatiquement une colonne "formule" (ex: D = A+B-C/10) pour tous les élèves
     * de la grille. Les lettres sont attribuées selon l'ordre des colonnes (A, B, C...).
     * Un élève dont une variable référencée n'a pas encore de note est laissé de côté.
     */
    suspend fun computeFormulaColumn(gridId: Long, column: GridColumnEntity) {
        val formula = column.formula ?: return
        val columns = db.gridColumnDao().getByGridOnce(gridId)
        val letterByColumnId = columns.mapIndexed { index, col -> col.id to ('A' + index) }.toMap()
        val students = db.studentDao().getByGridOnce(gridId)
        val scoresByStudent = db.studentScoreDao().getByGridOnce(gridId).groupBy { it.studentId }

        for (student in students) {
            val studentScores = scoresByStudent[student.id].orEmpty().associateBy { it.columnId }
            val variables = mutableMapOf<Char, Double>()
            var allPresent = true
            for (col in columns) {
                if (col.id == column.id) continue
                val letter = letterByColumnId[col.id] ?: continue
                val value = studentScores[col.id]?.value ?: run { allPresent = false; null }
                if (value != null) variables[letter] = value
            }
            if (!allPresent) continue
            val result = FormulaEvaluator.evaluate(formula, variables) ?: continue
            db.studentScoreDao().upsert(StudentScoreEntity(studentId = student.id, columnId = column.id, value = result, ocrUnconfirmed = false))
        }
    }

    // -------------------- Classes réutilisables --------------------

    val classes = db.classDao().observeAll()

    suspend fun createClass(name: String, studentNames: List<String>): Long {
        val classId = db.classDao().insert(ClassEntity(name = name))
        val cleaned = studentNames.filter { it.isNotBlank() }
        if (cleaned.isNotEmpty()) {
            db.classStudentDao().insertAll(cleaned.map { ClassStudentEntity(classId = classId, name = it) })
        }
        return classId
    }

    suspend fun getClass(classId: Long) = db.classDao().getById(classId)

    suspend fun getClassStudentsOnce(classId: Long) = db.classStudentDao().getByClassOnce(classId)

    suspend fun deleteClass(cls: ClassEntity) = db.classDao().delete(cls)

    suspend fun updateClassWithStudents(cls: ClassEntity, rows: List<EditableStudentRow>) {
        db.classDao().update(cls)
        val existing = db.classStudentDao().getByClassOnce(cls.id)
        syncEditableRows(
            existing = existing,
            idOf = { it.id },
            nameOf = { it.name },
            rows = rows,
            onDelete = { db.classStudentDao().delete(it) },
            onRename = { student, newName -> db.classStudentDao().update(student.copy(name = newName)) },
            onInsertNew = { newName -> db.classStudentDao().insert(ClassStudentEntity(classId = cls.id, name = newName)) }
        )
    }

    // -------------------- Scans en attente de vérification --------------------

    fun observePendingScans(gridId: Long) = db.pendingScanDao().observeByGrid(gridId)

    /** Enregistre une copie que l'OCR n'a pas pu attribuer avec confiance ; la photo est déjà sur le disque. */
    suspend fun addPendingScan(gridId: Long, columnId: Long, photoPath: String, suggestedStudentId: Long?, suggestedNoteValue: Double?) {
        db.pendingScanDao().insert(
            PendingScanEntity(
                gridId = gridId,
                columnId = columnId,
                photoPath = photoPath,
                suggestedStudentId = suggestedStudentId,
                suggestedNoteValue = suggestedNoteValue
            )
        )
    }

    /** Résout manuellement une copie en attente : enregistre la note et supprime la photo (stockage). */
    suspend fun resolvePendingScan(pending: PendingScanEntity, studentId: Long, value: Double) {
        setScore(studentId, pending.columnId, value, confirmedByUser = true)
        db.pendingScanDao().delete(pending)
        ScanPhotoStorage.delete(pending.photoPath)
    }

    /** Ignore une copie en attente sans enregistrer de note (ex: copie illisible/en double), supprime la photo. */
    suspend fun discardPendingScan(pending: PendingScanEntity) {
        db.pendingScanDao().delete(pending)
        ScanPhotoStorage.delete(pending.photoPath)
    }

    // -------------------- Tableau de bord --------------------

    fun observeDashboardStats(): Flow<DashboardStats> =
        combine(
            db.gridDao().observeAll(),
            db.studentDao().observeAll(),
            db.studentScoreDao().observeAll(),
            db.gridColumnDao().observeAll()
        ) { grids, allStudents, allScores, allColumns ->
            val columnsByGrid = allColumns.groupBy { it.gridId }
            val scoresByStudent = allScores.groupBy { it.studentId }
            val studentGrid = allStudents.associate { it.id to it.gridId }

            val studentsWithScore = allStudents.count { scoresByStudent.containsKey(it.id) }

            val percentages = allStudents.mapNotNull { student ->
                val scores = scoresByStudent[student.id] ?: return@mapNotNull null
                val columns = columnsByGrid[studentGrid[student.id]].orEmpty()
                val maxTotal = columns.sumOf { it.maxPoints }
                if (maxTotal <= 0.0) return@mapNotNull null
                val obtained = scores.sumOf { it.value }
                (obtained / maxTotal) * 100.0
            }

            DashboardStats(
                totalGrids = grids.size,
                totalStudents = allStudents.size,
                totalStudentsWithAtLeastOneScore = studentsWithScore,
                averageOn20 = if (percentages.isEmpty()) null else (percentages.average() / 100.0) * 20.0
            )
        }

    // -------------------- Utilitaire de synchro générique --------------------

    /**
     * Compare une liste d'entités existantes (identifiées par [idOf]) à une liste de lignes
     * éditées par l'utilisateur ([rows], voir [EditableStudentRow]), et déclenche :
     * - [onDelete] pour toute entité existante retirée de la liste,
     * - [onRename] pour toute entité existante dont le nom a changé,
     * - [onInsertNew] pour toute nouvelle ligne (id == null).
     * Utilisé à la fois pour les grilles et pour les classes, afin d'éviter la duplication.
     */
    private suspend fun <T> syncEditableRows(
        existing: List<T>,
        idOf: (T) -> Long,
        nameOf: (T) -> String,
        rows: List<EditableStudentRow>,
        onDelete: suspend (T) -> Unit,
        onRename: suspend (T, String) -> Unit,
        onInsertNew: suspend (String) -> Unit
    ) {
        val keptIds = rows.mapNotNull { it.id }.toSet()
        existing.filter { idOf(it) !in keptIds }.forEach { onDelete(it) }
        rows.forEach { row ->
            if (row.id != null) {
                val match = existing.find { idOf(it) == row.id }
                if (match != null && nameOf(match) != row.name) onRename(match, row.name)
            } else if (row.name.isNotBlank()) {
                onInsertNew(row.name)
            }
        }
    }
}
