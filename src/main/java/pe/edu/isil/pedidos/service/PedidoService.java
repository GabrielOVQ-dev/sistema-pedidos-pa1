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
import java.util.NoSuchElementException;

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
   * Busca un pedido por su ID.
   *
   * @param id ID del pedido.
   * @return El pedido encontrado.
   * @throws NoSuchElementException Si no existe un pedido con ese ID.
   */
  @TransactionAttribute(
          TransactionAttributeType.SUPPORTS
  )
  public Pedido buscarPedido(Long id) {
    Pedido pedido = entityManager.find(Pedido.class, id);

    if (pedido == null) {
      throw new NoSuchElementException("El pedido #" + id + " no existe.");
    }

    return pedido;
  }

  /**
   * Actualiza un pedido existente, ajustando el stock según corresponda:
   * - Si el producto no cambia, repone la cantidad original y descuenta la nueva.
   * - Si el producto cambia, repone stock del producto anterior y descuenta del nuevo.
   * Toda la operación es atómica: si alguna validación falla, la transacción se revierte
   * por completo (no quedan cambios parciales).
   *
   * @param id         ID del pedido a actualizar.
   * @param cliente    Nuevo nombre del cliente.
   * @param productoId ID del nuevo producto (puede ser el mismo).
   * @param cantidad   Nueva cantidad.
   * @return El pedido actualizado.
   * @throws NoSuchElementException   Si el pedido o el producto no existen.
   * @throws IllegalArgumentException Si los datos son inválidos.
   * @throws IllegalStateException    Si no hay stock suficiente.
   */
  @TransactionAttribute(
          TransactionAttributeType.REQUIRED
  )
  public Pedido actualizarPedido(Long id, String cliente, Long productoId, int cantidad) {
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

    Pedido pedido = entityManager.find(Pedido.class, id);

    if (pedido == null) {
      throw new NoSuchElementException("El pedido #" + id + " no existe.");
    }

    Producto productoActual = pedido.getProducto();
    Producto productoNuevo = entityManager.find(Producto.class, productoId);

    if (productoNuevo == null) {
      throw new NoSuchElementException("El producto seleccionado no existe.");
    }

    if (productoActual.getId().equals(productoNuevo.getId())) {
      // Mismo producto: primero se revierte la cantidad original reservada,
      // luego se valida y descuenta la nueva cantidad.
      productoActual.reponerStock(pedido.getCantidad());
      productoActual.descontarStock(cantidad);
    } else {
      // Cambio de producto: se repone al producto original y se descuenta del nuevo.
      productoActual.reponerStock(pedido.getCantidad());
      productoNuevo.descontarStock(cantidad);
    }

    BigDecimal total = productoNuevo.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    pedido.actualizar(cliente.trim(), productoNuevo, cantidad, total);

    return pedido;
  }

  /**
   * Elimina un pedido, reponiendo primero el stock del producto asociado.
   *
   * @param id ID del pedido a eliminar.
   * @throws NoSuchElementException Si no existe un pedido con ese ID.
   */
  @TransactionAttribute(
          TransactionAttributeType.REQUIRED
  )
  public void eliminarPedido(Long id) {
    Pedido pedido = entityManager.find(Pedido.class, id);

    if (pedido == null) {
      throw new NoSuchElementException("El pedido #" + id + " no existe.");
    }

    // Reponer stock antes de eliminar el registro.
    pedido.getProducto().reponerStock(pedido.getCantidad());

    entityManager.remove(pedido);
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