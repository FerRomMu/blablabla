package ar.edu.unq.eperdemic.services.impl

import ar.edu.unq.eperdemic.exceptions.DataDuplicationException
import ar.edu.unq.eperdemic.exceptions.DataNotFoundException
import ar.edu.unq.eperdemic.exceptions.IdNotFoundException
import ar.edu.unq.eperdemic.modelo.Camino
import ar.edu.unq.eperdemic.modelo.Ubicacion
import ar.edu.unq.eperdemic.modelo.UbicacionNeo
import ar.edu.unq.eperdemic.modelo.Vector
import ar.edu.unq.eperdemic.exceptions.UbicacionMuyLejana
import ar.edu.unq.eperdemic.exceptions.UbicacionNoAlcanzable
import ar.edu.unq.eperdemic.modelo.*
import ar.edu.unq.eperdemic.persistencia.repository.mongo.DistritoMongoRepository
import ar.edu.unq.eperdemic.persistencia.repository.mongo.UbicacionMongoRepository
import ar.edu.unq.eperdemic.persistencia.repository.neo.UbicacionNeoRepository
import ar.edu.unq.eperdemic.persistencia.repository.spring.UbicacionRepository
import ar.edu.unq.eperdemic.persistencia.repository.spring.VectorRepository
import ar.edu.unq.eperdemic.services.UbicacionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Transactional
@Service
class UbicacionServiceImpl(): UbicacionService {

    @Autowired lateinit var ubicacionNeoRepository: UbicacionNeoRepository
    @Autowired lateinit var ubicacionRepository : UbicacionRepository
    @Autowired lateinit var ubicacionMongoRepository: UbicacionMongoRepository
    @Autowired lateinit var distritoRepository: DistritoMongoRepository
    @Autowired lateinit var vectorRepository : VectorRepository

     fun moverVector(vectorAMover: Vector, ubicacionAMover: Ubicacion) {
            val listaDeVectores = ubicacionRepository.vectoresEn(ubicacionAMover.id).toList()
            val ubicacionOrigen = vectorAMover.ubicacion

             if(listaDeVectores.isNotEmpty()){
                 for (vector in listaDeVectores){
                     vectorAMover.intentarInfectar(vector)
                     vectorRepository.save(vector)
                 }
             }
             vectorAMover.ubicacion = ubicacionAMover
             vectorRepository.save(vectorAMover)

         val ubicacionOrigenMongo = ubicacionMongoRepository.findByNombre(ubicacionOrigen.nombre)
         val quedanInfectadosOrigen = ubicacionRepository.cantidadVectoresInfectados(ubicacionOrigen.nombre) > 0
         ubicacionOrigenMongo.hayAlgunInfectado = quedanInfectadosOrigen
         ubicacionMongoRepository.save(ubicacionOrigenMongo)

         val ubicacionDestinoMongo = ubicacionMongoRepository.findByNombre(ubicacionAMover.nombre)
         val quedanInfectadosDestino = ubicacionRepository.cantidadVectoresInfectados(ubicacionAMover.nombre) > 0
         ubicacionDestinoMongo.hayAlgunInfectado = quedanInfectadosDestino
         ubicacionMongoRepository.save(ubicacionDestinoMongo)
    }

    override fun mover(vectorId: Long, ubicacionid: Long){

        val vectorAMover = vectorRepository.findById(vectorId).get()
        val ubicacionAMover =  ubicacionRepository.findById(ubicacionid).get()

        val ubicacionOrigen = vectorAMover.ubicacion

        val errorNeo = ubicacionNeoRepository
            .validarMovimiento(
                ubicacionOrigen.nombre,
                ubicacionAMover.nombre,
                vectorAMover.tipo.puedeIrPor())

        val errorMongo = this.validarDistanciaMongo(ubicacionOrigen.nombre, ubicacionAMover.nombre)

        if(errorNeo == 1 || errorMongo){
            throw UbicacionMuyLejana("No es posible llegar desde la actual ubicación del vector a la nueva por medio de un camino.")
        }

        if(errorNeo == 2){
            throw UbicacionNoAlcanzable("Se intenta mover a un vector a través de un tipo de camino que no puede atravesar")
        }

        this.moverVector(vectorAMover,ubicacionAMover)
    }

    private fun validarDistanciaMongo(ubicacion1: String, ubicacion2: String): Boolean {
        val coordenada = ubicacionMongoRepository.findByNombre(ubicacion2).coordenada.toGeoJsonPoint().coordinates

        val result = !ubicacionMongoRepository.isLocationNearby(ubicacion1, coordenada[0], coordenada[1], 100000.0)

        return result
    }

    override fun expandir(ubicacionId: Long) {
            val vectores = ubicacionRepository.vectoresEn(ubicacionId).toMutableList()
            if (vectores.isNotEmpty()) {
                val dado = Randomizador.getInstance()
                val numeroAleatorio = dado.valor(0, vectores.size-1)
                val vectorContagioso = vectores.removeAt(numeroAleatorio)
                for(vector in vectores){
                    vectorContagioso.intentarInfectar(vector)
                    vectorRepository.save(vector)
                }
                vectorRepository.save(vectorContagioso)
            }
    }

    @Transactional(rollbackFor = [Exception::class], noRollbackFor = [DataIntegrityViolationException::class])
    override fun crearUbicacion(nombreUbicacion: String, coordenada: Coordenada): Ubicacion {
        val ubicacion = Ubicacion(nombreUbicacion)
        val ubicacionNeo = UbicacionNeo(nombreUbicacion)
        val ubicacionMongo = UbicacionMongo(coordenada, nombreUbicacion)
        ubicacionMongo.distrito = distritoRepository.findByPoint(ubicacionMongo.coordenada.latitud, ubicacionMongo.coordenada.longitud) ?: throw DataNotFoundException("No existe distrito para la coordenada dada.")
        try {
            ubicacionRepository.save(ubicacion)
            ubicacionNeoRepository.save(ubicacionNeo)
            ubicacionMongoRepository.save(ubicacionMongo)
            return ubicacion
        } catch (e: DataIntegrityViolationException) {
            throw DataDuplicationException("Ya existe una ubicación con ese nombre.")
        }
    }

    override fun recuperar(id: Long): Ubicacion {
        return ubicacionRepository.findById(id)
            .getOrNull() ?: throw IdNotFoundException("No se encontró una especie con el id dado.")
    }

    override fun recuperarTodos(): List<Ubicacion> {
            return ubicacionRepository.findAll().toList()
    }

    override fun vectoresEn(id: Long): List<Vector> {
        return ubicacionRepository.vectoresEn(id).toList()
    }

    override fun conectar(nombreDeUbicacion1: String, nombreDeUbicacion2: String, tipoCamino: Camino.TipoDeCamino) {
       try {
           val ubicacion1 = ubicacionNeoRepository.findByNombre(nombreDeUbicacion1)
           val ubicacion2 = ubicacionNeoRepository.findByNombre(nombreDeUbicacion2)

           val camino = Camino(ubicacion2, tipoCamino)
           ubicacion1.agregarCamino(camino)

           ubicacionNeoRepository.save(ubicacion1)
       } catch (e: Exception) {
           throw DataNotFoundException("No existe una ubicacion con el nombre dado")
       }
    }
    override fun conectados(nombreDeUbicacion:String): List<Ubicacion>{
        return ubicacionNeoRepository.conectados(nombreDeUbicacion).map { uNeo -> ubicacionRepository.findByNombre(uNeo.nombre) }
    }

    override fun moverMasCorto(vectorId: Long, nombreDeUbicacion: String){
        lateinit var ubicacionesAMover: List<UbicacionNeo>
        lateinit var vector: Vector
        try {
            vector = vectorRepository.findById(vectorId).get()
        } catch (e: Exception) {
            throw IdNotFoundException("No existe un vector con el id dado")
        }
        val tipo1 = vector.tipo.puedeIrPor()[0]
        val tipo2 = vector.tipo.puedeIrPor()[1]
        ubicacionesAMover =
            ubicacionNeoRepository.caminoMasCorto(vector.ubicacion.nombre, nombreDeUbicacion, tipo1, tipo2)

        if (ubicacionesAMover.isEmpty()) {
            throw throw UbicacionNoAlcanzable("no hay forma de llegar al destino dado")
        }

        val ubicacionesAIr = ubicacionesAMover.toMutableList()
        ubicacionesAIr.removeFirst()

        for (ubicacionNeo in ubicacionesAIr) {
            val ubicacion = ubicacionRepository.findByNombre(ubicacionNeo.nombre)
            this.moverVector(vector, ubicacion)
        }
    }
}