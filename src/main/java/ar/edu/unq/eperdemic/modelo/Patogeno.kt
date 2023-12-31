package ar.edu.unq.eperdemic.modelo

import java.io.Serializable
import javax.persistence.*

@Entity
@Table(name = "patogeno")
class Patogeno(var tipo: String) : Serializable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null

    fun cantidadDeEspecies() : Int {
        return this.especies.size
    }

    private var capacidadDeContagioHumano : Int = 0
    private var capacidadDeContagioInsecto : Int = 0
    private var capacidadDeContagioAnimal : Int = 0

    private var capacidadDeDefensa : Int = 0
    private var capacidadDeBiomecanizacion : Int = 0

    fun setCapacidadDeContagioHumano(capacidad :Int ){
        if (this.estanEnElRango(0,100,capacidad)){
            this.capacidadDeContagioHumano = capacidad
        }
    }

    fun setCapacidadDeContagioInsecto (capacidad :Int ){
        if (this.estanEnElRango(0,100,capacidad)){
            this.capacidadDeContagioInsecto = capacidad
        }
    }

    fun setCapacidadDeContagioAnimal (capacidad :Int ){
        if (this.estanEnElRango(0,100,capacidad)){
            this.capacidadDeContagioAnimal = capacidad
        }
    }

    fun setCapacidadDeDefensa (capacidad :Int ){
        if (this.estanEnElRango(0,100,capacidad)){
            this.capacidadDeDefensa = capacidad
        }
    }

    fun setCapacidadDeBiomecanizacion (capacidad :Int ){
        if (this.estanEnElRango(0,100,capacidad)){
            this.capacidadDeBiomecanizacion = capacidad
        }
    }


    fun estanEnElRango(min : Int, max : Int,numero : Int): Boolean{
        return numero <= max && numero >= min
    }


    override fun toString(): String {
        return tipo
    }

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], mappedBy = "patogeno")
    val especies: MutableList<Especie> = mutableListOf()

    fun crearEspecie(nombreEspecie: String, paisDeOrigen: String) : Especie {
        val especie = Especie(this,nombreEspecie,paisDeOrigen)
        this.especies.add(especie)

        return especie
    }

    fun capacidadDeContagioA(tipoVictima: TipoDeVector): Int {
        if(tipoVictima.esPersona()){
            return this.capacidadDeContagioHumano
        }else if (tipoVictima.esInsecto()){
            return this.capacidadDeContagioInsecto
        }else{
            return this.capacidadDeContagioAnimal
        }
    }

    fun getCapacidadDeBiomecanizacion(): Int {
        return this.capacidadDeBiomecanizacion
    }

    fun capacidadDeDefensa(): Int{
        return this.capacidadDeDefensa
    }

}