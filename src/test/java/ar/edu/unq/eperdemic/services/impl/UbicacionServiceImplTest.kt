package ar.edu.unq.eperdemic.services.impl

import ar.edu.unq.eperdemic.modelo.*
import ar.edu.unq.eperdemic.exceptions.DataDuplicationException
import ar.edu.unq.eperdemic.exceptions.DataNotFoundException
import ar.edu.unq.eperdemic.exceptions.UbicacionMuyLejana
import ar.edu.unq.eperdemic.exceptions.UbicacionNoAlcanzable
import ar.edu.unq.eperdemic.persistencia.repository.mongo.DistritoMongoRepository
import ar.edu.unq.eperdemic.persistencia.repository.mongo.UbicacionMongoRepository
import ar.edu.unq.eperdemic.persistencia.repository.neo.UbicacionNeoRepository
import ar.edu.unq.eperdemic.persistencia.repository.spring.UbicacionRepository
import ar.edu.unq.eperdemic.services.UbicacionService
import ar.edu.unq.eperdemic.services.VectorService
import ar.edu.unq.eperdemic.utils.DataService
import ar.edu.unq.eperdemic.utils.impl.DataServiceImpl
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UbicacionServiceImplTest {

    @Autowired lateinit var vectorService: VectorService

    @Autowired lateinit var ubicacionService: UbicacionService
    @Autowired lateinit var ubicacionNeoRepository: UbicacionNeoRepository
    @Autowired lateinit var ubicacionMongoRepository: UbicacionMongoRepository
    @Autowired lateinit var distritoMongoRepository: DistritoMongoRepository
    @Autowired lateinit var dataService: DataService

    lateinit var dado: Randomizador
    lateinit var coordenada: Coordenada
    lateinit var distrito: Distrito

    @BeforeEach
    fun setUp() {
        dado = Randomizador.getInstance()
        dado.estado = EstadoRandomizadorDeterminístico()

        coordenada = Coordenada(1.0, 2.0)

        distrito = Distrito("distritoA", listOf(coordenada, Coordenada(2.0, 1.0), Coordenada(2.2, 2.2)))
        distritoMongoRepository.save(distrito)
    }

    @Test
    fun `CrearUbicacion con coordenadas invalidas falla` () {
        val distrito1 = Distrito("Nombre Distrito 1",
            listOf(Coordenada(0.0, 0.0), Coordenada(3.0, 0.0), Coordenada(0.0, 3.0)))
        distritoMongoRepository.save(distrito1)

        val coordenadaInv = Coordenada(15.0,15.0)

        assertThrows(DataNotFoundException::class.java) {  ubicacionService.crearUbicacion("Ubicacion1", coordenadaInv) }
    }

    @Test
    fun  `mover vector a una ubicacion con un humano y un animal`() {

        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)
        val chaco = ubicacionService.crearUbicacion("Chaco", coordenada)

        ubicacionService.conectar("Cordoba", "Chaco", Camino.TipoDeCamino.CaminoTerreste)
        dataService.persistir(cordoba)

        var vectorAMover = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)

        var vectorVictima1 = vectorService.crearVector(TipoDeVector.Persona,chaco.id!!)
        var vectorVictima2 = vectorService.crearVector(TipoDeVector.Animal,chaco.id!!)

        val patogeno = Patogeno("Patogeni_SS")
        patogeno.setCapacidadDeContagioHumano(100)
        dataService.persistir(patogeno)


        val especieAContagiar = patogeno.crearEspecie("Especie_Sl","Honduras")
        dataService.persistir(especieAContagiar)

        vectorService.infectar(vectorAMover,especieAContagiar)

        assertEquals(vectorAMover.especiesContagiadas.size,1)
        assertEquals(vectorAMover.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima1.especiesContagiadas.size,0)
        assertEquals(vectorVictima2.especiesContagiadas.size,0)

        var vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)

        assertEquals(vectoresEnChaco.size,2)

        ubicacionService.mover(vectorAMover.id!!,chaco.id!!)

        vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)
        assertEquals(vectoresEnChaco.size,3)

        vectorAMover = vectorService.recuperarVector(vectorAMover.id!!)
        vectorVictima1 = vectorService.recuperarVector(vectorVictima1.id!!)
        vectorVictima2 =vectorService.recuperarVector(vectorVictima2.id!!)

        assertEquals(vectorAMover.especiesContagiadas.size,1)
        assertEquals(vectorAMover.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima1.especiesContagiadas.size,1)
        assertEquals(vectorVictima1.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima2.especiesContagiadas.size,0)

    }

    @Test
    fun  `cuando intento mover de la ubicacion actual del vector a una nueva por estar a mas de 100000km de distancia falla`() {

        val coordenadaLejana = Coordenada(60.0,40.0)
        val distrito2 = Distrito("Distritob", listOf(Coordenada(40.0,40.0), Coordenada(70.0,40.0), Coordenada(70.0, 70.0), Coordenada(40.0,70.0)))
        distritoMongoRepository.save(distrito2)
        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)
        val chaco = ubicacionService.crearUbicacion("Chaco", coordenadaLejana)

        ubicacionService.conectar("Cordoba", "Chaco", Camino.TipoDeCamino.CaminoTerreste)
        dataService.persistir(cordoba)

        val vectorAMover = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)

        val vectorVictima1 = vectorService.crearVector(TipoDeVector.Persona,chaco.id!!)
        val vectorVictima2 = vectorService.crearVector(TipoDeVector.Animal,chaco.id!!)

        val patogeno = Patogeno("Patogeni_SS")
        patogeno.setCapacidadDeContagioHumano(100)
        dataService.persistir(patogeno)


        val especieAContagiar = patogeno.crearEspecie("Especie_Sl","Honduras")
        dataService.persistir(especieAContagiar)

        vectorService.infectar(vectorAMover,especieAContagiar)

        assertEquals(vectorAMover.especiesContagiadas.size,1)
        assertEquals(vectorAMover.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima1.especiesContagiadas.size,0)
        assertEquals(vectorVictima2.especiesContagiadas.size,0)

        val vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)

        assertEquals(vectoresEnChaco.size,2)

        assertThrows(UbicacionMuyLejana::class.java) { ubicacionService.mover(vectorAMover.id!!,chaco.id!!) }

    }

    @Test
    fun  `mover vector insecto a una ubicacion con solo insectos`() {

        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)
        val chaco = ubicacionService.crearUbicacion("Chaco", coordenada)

        ubicacionService.conectar("Cordoba", "Chaco", Camino.TipoDeCamino.CaminoTerreste)
        dataService.persistir(cordoba)

        var vectorAMover = vectorService.crearVector(TipoDeVector.Insecto,cordoba.id!!)

        var vectorVictima1 = vectorService.crearVector(TipoDeVector.Insecto,chaco.id!!)
        var vectorVictima2 = vectorService.crearVector(TipoDeVector.Insecto,chaco.id!!)

        val patogeno = Patogeno("Patogeni_SS")
        patogeno.setCapacidadDeContagioInsecto(100)
        dataService.persistir(patogeno)

        val especieAContagiar = Especie(patogeno,"Especie_Sl","Honduras")
        dataService.persistir(especieAContagiar)

        vectorService.infectar(vectorAMover,especieAContagiar)

        assertEquals(vectorAMover.especiesContagiadas.size,1)
        assertEquals(vectorAMover.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima1.especiesContagiadas.size,0)
        assertEquals(vectorVictima2.especiesContagiadas.size,0)

        var vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)

        assertEquals(vectoresEnChaco.size,2)

        ubicacionService.mover(vectorAMover.id!!,chaco.id!!)

        vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)
        assertEquals(vectoresEnChaco.size,3)

        vectorAMover = vectorService.recuperarVector(vectorAMover.id!!)
        vectorVictima1 = vectorService.recuperarVector(vectorVictima1.id!!)
        vectorVictima2 =vectorService.recuperarVector(vectorVictima2.id!!)

        assertEquals(vectorAMover.especiesContagiadas.size,1)
        assertEquals(vectorAMover.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorVictima1.especiesContagiadas.size,0)
        assertEquals(vectorVictima2.especiesContagiadas.size,0)

    }

    @Test
    fun  `mover vector a ubicacion vacia`() {

        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)
        val chaco = ubicacionService.crearUbicacion("Chaco", coordenada)

        ubicacionService.conectar("Cordoba", "Chaco", Camino.TipoDeCamino.CaminoTerreste)
        dataService.persistir(cordoba)

        var vectorAMover = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)

        val patogeno = Patogeno("Patogeni_SS")
        patogeno.setCapacidadDeContagioInsecto(100)
        dataService.persistir(patogeno)

        val especieAContagiar = patogeno.crearEspecie("Especie_Sl","Honduras")
        dataService.persistir(especieAContagiar)

        vectorService.infectar(vectorAMover,especieAContagiar)
        assertEquals(vectorAMover.especiesContagiadas.size,1)

        val vectoresEnChaco = ubicacionService.vectoresEn(chaco.id!!)
        assertEquals(vectoresEnChaco.size,0)

        ubicacionService.mover(vectorAMover.id!!,chaco.id!!)

        vectorAMover = vectorService.recuperarVector(vectorAMover.id!!)
        assertEquals(vectorAMover.ubicacion.id,chaco.id)

    }

    @Test
    fun `Expandir en una ubicacion`() {
        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)

        var vectorLocal = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)
        var vectorLocal2 = vectorService.crearVector(TipoDeVector.Animal,cordoba.id!!)
        var vectorAExpandir = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)


        val patogeno = Patogeno("Patogeni_SS")
        patogeno.setCapacidadDeContagioHumano(100)
        dataService.persistir(patogeno)

        val especieAContagiar = Especie(patogeno,"Especie_Sl","Honduras")
        dataService.persistir(especieAContagiar)

        vectorService.infectar(vectorAExpandir,especieAContagiar)

        assertEquals(vectorAExpandir.especiesContagiadas.size,1)
        assertEquals(vectorAExpandir.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorLocal.especiesContagiadas.size,0)
        assertEquals(vectorLocal2.especiesContagiadas.size,0)

        ubicacionService.expandir(cordoba.id!!)

        vectorAExpandir = vectorService.recuperarVector(vectorAExpandir.id!!)
        vectorLocal = vectorService.recuperarVector(vectorLocal.id!!)
        vectorLocal2 =vectorService.recuperarVector(vectorLocal2.id!!)

        assertEquals(vectorAExpandir.especiesContagiadas.size,1)
        assertEquals(vectorAExpandir.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorLocal.especiesContagiadas.size,1)
        assertEquals(vectorLocal.especiesContagiadas.first().id, especieAContagiar.id)
        assertEquals(vectorLocal2.especiesContagiadas.size,0)

    }

    @Test
    fun `Expandir en una ubicacion sin contagios no hace nada`() {
        val cordoba = ubicacionService.crearUbicacion("Cordoba", coordenada)

        val vectorSinContagiar = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)
        val vectorSinContagiar2 = vectorService.crearVector(TipoDeVector.Animal,cordoba.id!!)
        val vectorSinContagiar3 = vectorService.crearVector(TipoDeVector.Persona,cordoba.id!!)

        ubicacionService.expandir(cordoba.id!!)

        assertEquals(vectorSinContagiar.especiesContagiadas.size,0)
        assertEquals(vectorSinContagiar2.especiesContagiadas.size,0)
        assertEquals(vectorSinContagiar3.especiesContagiadas.size,0)
    }


    @Test
    fun `si creo una ubicacion esta recibe un id`() {
        val ubicacion = ubicacionService.crearUbicacion("ubicacionTest", coordenada)
        assertNotNull(ubicacion.id)
    }

    @Test
    fun `si creo una ubicacion se guarda una ubicacionNeo con ese nombre tambien`() {
        val ubicacion = ubicacionService.crearUbicacion("ubicacionTest", coordenada)

        val ubicacionNeoCreada = ubicacionNeoRepository.findByNombre(ubicacion.nombre)

        assertEquals("ubicacionTest", ubicacionNeoCreada.nombre)
    }

    @Test
    fun `si creo una ubicacion la puedo recuperar`() {
        val ubicacion = ubicacionService.crearUbicacion("ubicacionTest", coordenada)
        val ubicacionRecuperada = ubicacionService.recuperar(ubicacion.id!!)

        assertEquals(ubicacion.nombre, ubicacionRecuperada.nombre)
        assertEquals(ubicacion.id, ubicacionRecuperada.id)
    }

    @Test
    fun `si trato de crear dos ubicaciones con el mismo nombre recibo error`() {
        ubicacionService.crearUbicacion("ubicacionRepetida", coordenada)

        assertThrows(DataDuplicationException::class.java) { ubicacionService.crearUbicacion("ubicacionRepetida", coordenada) }
    }

    @Test
    fun `si trato de recuperar todos llegan todos`() {
        val ubicacionesPersistidas = dataService.crearSetDeDatosIniciales().filterIsInstance<Ubicacion>()
        val ubicaciones = ubicacionService.recuperarTodos()

        assertEquals(ubicacionesPersistidas.size, ubicaciones.size)
        assertTrue(
            ubicaciones.all { ubicacion ->
                ubicacionesPersistidas.any{
                    it.id == ubicacion.id &&
                            it.nombre == ubicacion.nombre
                }
            }
        )
    }

    @Test
    fun `si trato de recuperar todos y no hay nadie simplemente recibo 0`() {

        val ubicaciones = ubicacionService.recuperarTodos()

        assertEquals(0, ubicaciones.size)
    }

    @Test
    fun `se conectan dos ubicaciones existentes por medio terrestre`() {
        val Bera = ubicacionService.crearUbicacion("ubicacion neo 1", coordenada)
        val ubicacion2 = ubicacionService.crearUbicacion("ubicacion neo 2", coordenada)

        ubicacionService.conectar(Bera.nombre, ubicacion2.nombre, Camino.TipoDeCamino.CaminoTerreste)

        val ubicacionNeo1 = ubicacionNeoRepository.findByNombre(Bera.nombre)

        assertEquals(ubicacionNeo1.caminos[0].tipo, Camino.TipoDeCamino.CaminoTerreste)
        assertEquals(ubicacionNeo1.caminos[0].ubicacioDestino.nombre, ubicacion2.nombre)
    }

    @Test
    fun `se establecen 2 conexiones unidireccionales entre dos ubicaciones`() {
        val ubicacion1 = ubicacionService.crearUbicacion("ubicacion neo 1", coordenada)
        val ubicacion2 = ubicacionService.crearUbicacion("ubicacion neo 2", coordenada)

        ubicacionService.conectar(ubicacion1.nombre, ubicacion2.nombre, Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar(ubicacion2.nombre, ubicacion1.nombre, Camino.TipoDeCamino.CaminoAereo)

        val ubicacionNeo1 = ubicacionNeoRepository.findByNombre(ubicacion1.nombre)
        val ubicacionNeo2 = ubicacionNeoRepository.findByNombre(ubicacion2.nombre)

        assertEquals(ubicacionNeo1.caminos[0].tipo, Camino.TipoDeCamino.CaminoTerreste)
        assertEquals(ubicacionNeo1.caminos[0].ubicacioDestino.nombre, ubicacion2.nombre)
        assertEquals(ubicacionNeo2.caminos[0].tipo, Camino.TipoDeCamino.CaminoAereo)
        assertEquals(ubicacionNeo2.caminos[0].ubicacioDestino.nombre, ubicacion1.nombre)
    }

    @Test
    fun `si intento conectar dos ubicaciones que no existen falla`() {

       assertThrows(DataNotFoundException::class.java)
            { ubicacionService.conectar("ubicacion inexistente 1",
                                        "ubicacion inexistente 2",
                                        Camino.TipoDeCamino.CaminoTerreste) }

    }

    @Test
    fun `si pido los caminos conectados a la Ubicacion con nombre Quilmes me los devuelve` () {
        ubicacionService.crearUbicacion("Bera", coordenada)
        ubicacionService.crearUbicacion("Ubicacion2", coordenada)
        ubicacionService.crearUbicacion("Ubicacion3", coordenada)
        ubicacionService.crearUbicacion("Ubicacion4", coordenada)
        ubicacionService.crearUbicacion("Ubicacion5", coordenada)

        ubicacionService.conectar("Ubicacion2", "Bera", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion2", "Ubicacion3", Camino.TipoDeCamino.CaminoAereo)

        ubicacionService.conectar("Ubicacion3", "Ubicacion4", Camino.TipoDeCamino.CaminoAereo)
        ubicacionService.conectar("Ubicacion4", "Ubicacion3", Camino.TipoDeCamino.CaminoAereo)
        ubicacionService.conectar("Bera", "Ubicacion3", Camino.TipoDeCamino.CaminoAereo)
        ubicacionService.conectar("Ubicacion4", "Ubicacion5", Camino.TipoDeCamino.CaminoAereo)

        val ubicacionesConectadas = ubicacionService.conectados("Ubicacion2")

        val ubicacion2 = ubicacionNeoRepository.findByNombre("Ubicacion2")

        assertEquals(ubicacion2.caminos.size, ubicacionesConectadas.size)

        val nombreUbicacionesConectadas = ubicacionesConectadas.map { u -> u.nombre}
        assertTrue(nombreUbicacionesConectadas.contains("Ubicacion3"))
        assertTrue(nombreUbicacionesConectadas.contains("Bera"))
    }

    @Test
    fun `si pido los caminos conectados a la Ubicacion que no existe devuelve una lista vacia` () {

        assertEquals(0, ubicacionService.conectados("ubicacion inexistente 1").size)
    }

    @Test
    fun `Mover mas corto pero no hay camino` () {
        val ubicacion1 = ubicacionService.crearUbicacion("Ubicacion1", coordenada)
        ubicacionService.crearUbicacion("Ubicacion2", coordenada)
        ubicacionService.crearUbicacion("Ubicacion3", coordenada)
        ubicacionService.crearUbicacion("Ubicacion4", coordenada)

        ubicacionService.conectar("Ubicacion1", "Ubicacion2", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion2", "Ubicacion3", Camino.TipoDeCamino.CaminoTerreste)

        val vectorAMover = vectorService.crearVector(TipoDeVector.Persona,ubicacion1.id!!)

        assertThrows(UbicacionNoAlcanzable::class.java) { ubicacionService.moverMasCorto(vectorAMover.id!!,"Ubicacion4") }
    }

    @Test
    fun `Mover mas corto hay camino pero no lo puede recorrer` () {
        val ubicacion1 = ubicacionService.crearUbicacion("Ubicacion1", coordenada)
        ubicacionService.crearUbicacion("Ubicacion2", coordenada)
        ubicacionService.crearUbicacion("Ubicacion3", coordenada)

        ubicacionService.conectar("Ubicacion1", "Ubicacion2", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion2", "Ubicacion3", Camino.TipoDeCamino.CaminoAereo)

        val vectorAMover = vectorService.crearVector(TipoDeVector.Persona,ubicacion1.id!!)

        assertThrows(UbicacionNoAlcanzable::class.java) { ubicacionService.moverMasCorto(vectorAMover.id!!,"Ubicacion4") }
    }

    @Test
    fun `Mover mas corto hay mas de un camino que puede recorre y elije el mas corto y infecta` () {

        // Setup //

        val ubicacion1 = ubicacionService.crearUbicacion("Ubicacion1", coordenada)
        val ubicacion2 = ubicacionService.crearUbicacion("Ubicacion2", coordenada)
        val ubicacion3 = ubicacionService.crearUbicacion("Ubicacion3", coordenada)
        val ubicacion4 = ubicacionService.crearUbicacion("Ubicacion4", coordenada)
        val ubicacion5 = ubicacionService.crearUbicacion("Ubicacion5", coordenada)

        ubicacionService.conectar("Ubicacion1", "Ubicacion2", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion1", "Ubicacion4", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion2", "Ubicacion3", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion2", "Ubicacion4", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion3", "Ubicacion5", Camino.TipoDeCamino.CaminoTerreste)
        ubicacionService.conectar("Ubicacion4", "Ubicacion5", Camino.TipoDeCamino.CaminoTerreste)

        val vectorAMover = vectorService.crearVector(TipoDeVector.Persona,ubicacion1.id!!)

        var vector1 = vectorService.crearVector(TipoDeVector.Persona,ubicacion2.id!!)
        var vector2 = vectorService.crearVector(TipoDeVector.Persona,ubicacion2.id!!)
        var vector3 = vectorService.crearVector(TipoDeVector.Persona,ubicacion3.id!!)
        var vector4 = vectorService.crearVector(TipoDeVector.Persona,ubicacion4.id!!)
        var vector5 = vectorService.crearVector(TipoDeVector.Animal,ubicacion4.id!!)
        var vector6 = vectorService.crearVector(TipoDeVector.Persona,ubicacion5.id!!)

        val patogeno1 = Patogeno("patogeno1")
        val especie1 =  patogeno1.crearEspecie("especie1","P.ORIGEN")

        patogeno1.setCapacidadDeContagioHumano(100)
        patogeno1.setCapacidadDeContagioInsecto(100)
        patogeno1.setCapacidadDeContagioAnimal(0)

        dataService.persistir(patogeno1)
        dataService.persistir(especie1)
        vectorService.infectar(vectorAMover,especie1)

        // Excercise //

        ubicacionService.moverMasCorto(vectorAMover.id!!,"Ubicacion5")

        val vectorMovido = vectorService.recuperarVector(vectorAMover.id!!)
        vector1 = vectorService.recuperarVector(vector1.id!!)
        vector2 = vectorService.recuperarVector(vector2.id!!)
        vector3 = vectorService.recuperarVector(vector3.id!!)
        vector4 = vectorService.recuperarVector(vector4.id!!)
        vector5 = vectorService.recuperarVector(vector5.id!!)
        vector6 = vectorService.recuperarVector(vector6.id!!)

        // Verify //

        assertEquals(0,vector1.especiesContagiadas.size)
        assertEquals(0,vector2.especiesContagiadas.size)
        assertEquals(0,vector3.especiesContagiadas.size)
        assertEquals(0,vector5.especiesContagiadas.size)

        assertEquals(1,vector4.especiesContagiadas.size)
        assertEquals(especie1.patogeno.tipo,vector4.especiesContagiadas.toList()[0].patogeno.tipo)
        assertEquals(especie1.nombre,vector4.especiesContagiadas.toList()[0].nombre)
        assertEquals(especie1.paisDeOrigen,vector4.especiesContagiadas.toList()[0].paisDeOrigen)

        assertEquals(1,vector6.especiesContagiadas.size)
        assertEquals(especie1.patogeno.tipo,vector6.especiesContagiadas.toList()[0].patogeno.tipo)
        assertEquals(especie1.nombre,vector6.especiesContagiadas.toList()[0].nombre)
        assertEquals(especie1.paisDeOrigen,vector6.especiesContagiadas.toList()[0].paisDeOrigen)

        assertEquals(1,vectorMovido.especiesContagiadas.size)
        assertEquals(especie1.patogeno.tipo,vectorMovido.especiesContagiadas.toList()[0].patogeno.tipo)
        assertEquals(especie1.nombre,vectorMovido.especiesContagiadas.toList()[0].nombre)
        assertEquals(especie1.paisDeOrigen,vectorMovido.especiesContagiadas.toList()[0].paisDeOrigen)
        assertEquals(ubicacion5.nombre,vectorMovido.ubicacion.nombre)
    }

    @Test
    fun `Mover el unico vector infectado a otra ubicacion vacia` () {
        val ubicacion1 = ubicacionService.crearUbicacion("Ubicacion1", coordenada)

        val ubicacion2 = ubicacionService.crearUbicacion("Ubicacion2", coordenada)
        ubicacionService.conectar("Ubicacion1", "Ubicacion2", Camino.TipoDeCamino.CaminoTerreste)

        val vectorAMover = vectorService.crearVector(TipoDeVector.Persona,ubicacion1.id!!)
        val patogeno1 = Patogeno("patogeno1")
        val especie1 =  patogeno1.crearEspecie("especie1","P.ORIGEN")

        dataService.persistir(patogeno1)
        dataService.persistir(especie1)
        vectorService.infectar(vectorAMover,especie1)

        assertTrue(ubicacionMongoRepository.findByNombre(ubicacion1.nombre).hayAlgunInfectado)
        assertFalse(ubicacionMongoRepository.findByNombre(ubicacion2.nombre).hayAlgunInfectado)

        ubicacionService.mover(vectorAMover.id!!,ubicacion2.id!!)

        assertFalse(ubicacionMongoRepository.findByNombre(ubicacion1.nombre).hayAlgunInfectado)
        assertTrue(ubicacionMongoRepository.findByNombre(ubicacion2.nombre).hayAlgunInfectado)

    }



    @AfterEach
    fun tearDown() {
        dataService.eliminarTodo()
        ubicacionNeoRepository.deleteAll()
        ubicacionMongoRepository.deleteAll()
        distritoMongoRepository.deleteAll()
    }

}