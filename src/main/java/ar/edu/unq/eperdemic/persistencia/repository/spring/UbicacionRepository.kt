package ar.edu.unq.eperdemic.persistencia.repository.spring

import ar.edu.unq.eperdemic.modelo.Ubicacion
import ar.edu.unq.eperdemic.modelo.Vector
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UbicacionRepository : CrudRepository<Ubicacion, Long> {

    @Query("FROM Vector v " +
            "WHERE v.ubicacion.id = :idUbicacion")
    fun vectoresEn(@Param("idUbicacion") ubicacionId: Long?): List<Vector>

    @Query("SELECT COUNT(*)\n" +
            "FROM Vector v\n" +
            "WHERE v.ubicacion.nombre = :nombreUbicacion")
    fun cantidadVectoresPresentes(@Param("nombreUbicacion") nombreDeLaUbicacion: String) : Long

    @Query("select count(distinct v.id) " +
            "from Vector v " +
            "join v.especiesContagiadas es " +
            "where v.ubicacion.nombre = :nombre")
    fun cantidadVectoresInfectados(@Param("nombre") nombreDeLaUbicacion: String) : Long

    @Query("SELECT es.nombre FROM especie_vector_contagiado evc\n" +
            "JOIN especie es ON es.id = evc.especie_id\n" +
            "JOIN vector v ON v.id = evc.vector_id\n" +
            "JOIN ubicacion u ON u.id = v.ubicacion_id\n" +
            "WHERE u.nombre = :nombreUbicacion\n" +
            "GROUP BY es.nombre\n" +
            "ORDER BY COUNT(v.id) DESC\n" +
            "LIMIT 1", nativeQuery = true)
    fun nombreEspecieQueMasInfectaVectores(@Param("nombreUbicacion") nombreDeLaUbicacion: String) : String
}