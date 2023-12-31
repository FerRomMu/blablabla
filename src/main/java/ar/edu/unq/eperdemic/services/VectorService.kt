package ar.edu.unq.eperdemic.services

import ar.edu.unq.eperdemic.modelo.Especie
import ar.edu.unq.eperdemic.modelo.TipoDeVector
import ar.edu.unq.eperdemic.modelo.Vector

interface VectorService {

    fun infectar(vector: Vector, especie: Especie)
    fun enfermedades(vectorId: Long): List<Especie>

    /* Operaciones CRUD */
    fun crearVector(tipo: TipoDeVector, ubicacionId: Long): Vector
    fun recuperarVector(vectorId: Long): Vector
    fun borrarVector(vectorId: Long)
    fun recuperarTodos(): List<Vector>

}