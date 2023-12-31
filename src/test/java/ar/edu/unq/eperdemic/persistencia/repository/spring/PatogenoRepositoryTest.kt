package ar.edu.unq.eperdemic.persistencia.repository.spring

import ar.edu.unq.eperdemic.modelo.Especie
import ar.edu.unq.eperdemic.modelo.Patogeno
import ar.edu.unq.eperdemic.modelo.Ubicacion
import ar.edu.unq.eperdemic.utils.DataService
import org.junit.jupiter.api.*

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatogenoRepositoryTest {

    @Autowired lateinit var patogenoRepository: PatogenoRepository
    lateinit var patogeno: Patogeno
    @Autowired lateinit var data: DataService

    @BeforeEach
    fun setUp() {
        patogeno = Patogeno("Gripe")
    }

    @Test
    fun `si creo un Patogeno al guardarlo se le asigna un id`() {

        assertNull(patogeno.id)

        patogenoRepository.save(patogeno)

        assertNotNull(patogeno.id)

    }

    @Test
    fun `si guardo un Patogeno con id se actualiza`() {

        data.persistir(patogeno)
        assertEquals(0, patogeno.cantidadDeEspecies())

        patogeno.crearEspecie("especieA", "Japon")

        patogenoRepository.save(patogeno)
        val patogenoActualizado = patogenoRepository.findById(patogeno.id!!).get()

        assertEquals(1, patogenoActualizado.cantidadDeEspecies())
    }

    @Test
    fun `si trato de recuperar un Patogeno existente con su id lo obtengo`() {
        data.persistir(patogeno)
        val patogenoRecuperado =  patogenoRepository.findById(patogeno.id!!).get()

        assertEquals(patogeno.id, patogenoRecuperado.id)
        assertEquals(patogeno.tipo, patogenoRecuperado.tipo)
        assertEquals(patogeno.cantidadDeEspecies(), patogenoRecuperado.cantidadDeEspecies())
    }

    @Test
    fun `si trato de recuperar un Patogeno inexistente devuelve null`() {
        assertTrue(patogenoRepository.findById(10000001).isEmpty)
    }

    @Test
    fun `si recupero todos los patogenos recibo todos`(){

        val patogenosPersistidos = data.crearSetDeDatosIniciales().filterIsInstance<Patogeno>()
        val recuperados = patogenoRepository.findAll().toList()
        assertEquals(patogenosPersistidos.size, recuperados.size)
        assertTrue(
            recuperados.all {patogeno ->
                patogenosPersistidos.any {
                    it.id == patogeno.id &&
                            it.tipo == patogeno.tipo
                }
            }
        )
    }

    @Test
    fun `si borro un patogeno este deja de estar persistido`(){

        data.persistir(patogeno)

        patogenoRepository.deleteById(patogeno.id!!)

        assertTrue(patogenoRepository.findById(patogeno.id!!).isEmpty)
    }

    @Test
    fun `si borro un patogeno  con id invalido no devuelve nada`() {

        assertThrows(NullPointerException::class.java) { patogenoRepository.deleteById(patogeno.id!!) }
    }

    @Test
    fun `si trato de recuperar las especies de un patogeno las devuelve`() {
        patogeno = Patogeno("Gripe")

        patogeno.crearEspecie("virusT", "mansion spencer")
        patogeno.crearEspecie("virusG", "raccoon city")
        patogeno.crearEspecie("virus progenitor", "montanas arklay")
        data.persistir(patogeno)

        val especies: List<String> = patogenoRepository.especiesDePatogeno(patogeno.id!!).map{ e -> e.nombre}

        assertEquals(3, especies.size)
        assertTrue(especies.contains("virusT"))
        assertTrue(especies.contains("virusG"))
        assertTrue(especies.contains("virus progenitor"))

    }

    @Test
    fun `si hay pandemia por la especie dada recibo verdadero en esPandemia`() {
        val especiePandemica = data.crearPandemiaPositiva()
        assertTrue(patogenoRepository.esPandemia(especiePandemica.id!!))
    }

    @Test
    fun `si no hay pandemia por la especie dada recibo falso en esPandemia`() {
        val especieNoPandemica = data.crearSetDeDatosIniciales().filterIsInstance<Especie>().first()
        assertFalse(patogenoRepository.esPandemia(especieNoPandemica.id!!))
    }

    @AfterEach
    fun tearDown() {
        data.eliminarTodo()
    }

}