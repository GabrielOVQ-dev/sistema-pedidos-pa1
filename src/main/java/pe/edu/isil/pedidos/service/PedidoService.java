package pe.edu.isil.pedidos.service;

import pe.edu.isil.pedidos.domain.Pedido;
import pe.edu.isil.pedidos.domain.Producto;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio EJB que maneja la lógica de negocio relacionada con los pedidos.
 */
@Stateless
public class PedidoService {

  @PersistenceContext(
      unitName = "PedidosPU"
  )
  private EntityManager entityManager;

  /**
   * Registra un nuevo pedido en el sistema.
   *
   * @param cliente    Nombre del cliente que realiza el pedido.
   * @param productoId ID del producto que se desea comprar.
   * @param cantidad   Cantidad de productos a comprar.
   * @return El pedido registrado.
   * @throws IllegalArgumentException Si alguno de los parámetros es inválido o si el producto no existe.
   */
  @TransactionAttribute(
      TransactionAttributeType.REQUIRED
  )
  public Pedido registrarPedido(String cliente, Long productoId, int cantidad) {
    if (cliente == null ||
        cliente.isBlank()) {
      throw new IllegalArgumentException("El cliente es obligatorio.");
    }

    if (productoId == null) {
      throw new IllegalArgumentException("Debe seleccionar un producto.");
    }

    if (cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
    }

    Producto producto = entityManager.find(Producto.class, productoId);

    if (producto == null) {
      throw new IllegalArgumentException("El producto no existe.");
    }

    // REGLA DE NEGOCIO
    producto.descontarStock(cantidad);

    BigDecimal total = producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));

    Pedido pedido = new Pedido(cliente.trim(), producto, cantidad, total);

    entityManager.persist(pedido);

    return pedido;
  }

  /**
   * Lista todos los productos disponibles en el sistema.
   *
   * @return Lista de productos.
   */
  @TransactionAttribute(
      TransactionAttributeType.REQUIRED
  )
  public List<Producto> listarProductos() {
    inicializarProductosSiEsNecesario();
    return entityManager
        .createQuery(
            """
            select p
            from Producto p
            order by p.id
            """,
            Producto.class
        )
        .getResultList();
  }

  /**
   * Lista todos los pedidos realizados en el sistema.
   *
   * @return Lista de pedidos.
   */
  @TransactionAttribute(
      TransactionAttributeType.SUPPORTS
  )
  public List<Pedido> listarPedidos() {
    return entityManager
        .createQuery(
            """
            select p
            from Pedido p
            join fetch p.producto
            order by p.id desc
            """,
            Pedido.class
        )
        .getResultList();
  }

  /**
   * Inicializa algunos productos de ejemplo si no existen en la base de datos.
   */
  private void inicializarProductosSiEsNecesario() {
    Long cantidad =
        entityManager
            .createQuery(
                """
                select count(p)
                from Producto p
                """,
                Long.class
            )
            .getSingleResult();

    if (cantidad == 0) {
      entityManager.persist(
          new Producto(
              "Laptop",
              new BigDecimal("2500.00"),
              5
          )
      );

      entityManager.persist(
          new Producto(
              "Monitor",
              new BigDecimal("850.00"),
              8
          )
      );

      entityManager.persist(
          new Producto(
              "Teclado",
              new BigDecimal("120.00"),
              15
          )
      );
    }
  }

}
