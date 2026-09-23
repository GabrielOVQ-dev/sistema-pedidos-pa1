package pe.edu.isil.pedidos.web;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import pe.edu.isil.pedidos.domain.Pedido;
import pe.edu.isil.pedidos.domain.Producto;
import pe.edu.isil.pedidos.service.PedidoService;

/**
 * Servlet que maneja las solicitudes relacionadas con los pedidos.
 */
@WebServlet("/pedidos")
public class PedidoServlet extends HttpServlet {

  @EJB
  private PedidoService pedidoService;

  /**
   * Maneja las solicitudes GET para mostrar la página de pedidos.
   *
   * @param request  Objeto que contiene la solicitud del cliente.
   * @param response Objeto que contiene la respuesta que se enviará al cliente.
   * @throws ServletException Si ocurre un error en el servlet.
   * @throws IOException      Si ocurre un error de entrada/salida.
   */
  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {
    renderizarPagina(response, null);
  }

  /**
   * Maneja las solicitudes POST para registrar un nuevo pedido.
   * @param request  Objeto que contiene la solicitud del cliente.
   * @param response Objeto que contiene la respuesta que se enviará al cliente.
   * @throws ServletException Si ocurre un error en el servlet.
   * @throws IOException      Si ocurre un error de entrada/salida.
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());

    try {
      String cliente = request.getParameter("cliente");
      Long productoId = Long.valueOf(request.getParameter("productoId"));
      int cantidad = Integer.parseInt(request.getParameter("cantidad"));

      Pedido pedido = pedidoService.registrarPedido(cliente, productoId, cantidad);

      response.sendRedirect(request.getContextPath() + "/pedidos?creado=" + pedido.getId());
    } catch (NumberFormatException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      renderizarPagina(response, "Producto o cantidad inválidos.");
    } catch (IllegalArgumentException | IllegalStateException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      renderizarPagina(response, e.getMessage());
    }
  }

  /**
   * Renderiza la página HTML con el formulario de registro de pedidos y la lista de pedidos existentes.
   *
   * @param response Objeto que contiene la respuesta que se enviará al cliente.
   * @param error    Mensaje de error a mostrar en la página, si lo hay.
   * @throws IOException Si ocurre un error de entrada/salida.
   */
  private void renderizarPagina(HttpServletResponse response, String error) throws IOException {
    List<Producto> productos = pedidoService.listarProductos();
    List<Pedido> pedidos = pedidoService.listarPedidos();

    response.setContentType("text/html;charset=UTF-8");

    try (PrintWriter out = response.getWriter()) {
      out.println("""
                    <!doctype html>
                    <html lang=\"es\">
                    <head>
                      <meta charset=\"UTF-8\">
                      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
                      <title>Sistema de Pedidos - ISIL</title>
                      <style>
                        body { font-family: Arial, sans-serif; max-width: 980px; margin: 32px auto; padding: 0 16px; }
                        form { display: grid; grid-template-columns: 2fr 2fr 1fr auto; gap: 12px; align-items: end; }
                        label { display: flex; flex-direction: column; gap: 6px; font-weight: 600; }
                        input, select, button { padding: 10px; font-size: 14px; }
                        button { cursor: pointer; }
                        table { width: 100%; border-collapse: collapse; margin-top: 24px; }
                        th, td { border: 1px solid #ccc; padding: 9px; text-align: left; }
                        th { background: #f2f2f2; }
                        .error { background: #ffe7e7; border: 1px solid #d33; padding: 10px; margin: 16px 0; }
                        .nota { background: #f5f5f5; padding: 10px; margin: 16px 0; }
                      </style>
                    </head>
                    <body>
                      <h1>Sistema de Pedidos</h1>
                      <p class=\"nota\">Flujo: Navegador → PedidoServlet → PedidoService (EJB) → JPA → H2.</p>
                    """);

      if (error != null) {
        out.printf("<div class=\"error\">%s</div>%n", escapeHtml(error));
      }

      out.println("""
                      <h2>Registrar pedido</h2>
                      <form method=\"post\">
                        <label>Cliente
                          <input name=\"cliente\" required maxlength=\"120\" placeholder=\"Ej. Ana Torres\">
                        </label>
                        <label>Producto
                          <select name=\"productoId\" required>
                    """);

      for (Producto producto : productos) {
        out.printf(
            "<option value=\"%d\">%s - S/ %s - stock: %d</option>%n",
            producto.getId(),
            escapeHtml(producto.getNombre()),
            producto.getPrecio().toPlainString(),
            producto.getStock());
      }

      out.println("""
                          </select>
                        </label>
                        <label>Cantidad
                          <input name=\"cantidad\" type=\"number\" min=\"1\" value=\"1\" required>
                        </label>
                        <button type=\"submit\">Registrar</button>
                      </form>

                      <h2>Pedidos registrados</h2>
                      <table>
                        <thead>
                          <tr>
                            <th>ID</th><th>Cliente</th><th>Producto</th><th>Cantidad</th><th>Total</th><th>Fecha</th>
                          </tr>
                        </thead>
                        <tbody>
                    """);

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
      for (Pedido pedido : pedidos) {
        out.printf(
            "<tr><td>%d</td><td>%s</td><td>%s</td><td>%d</td><td>S/ %s</td><td>%s</td></tr>%n",
            pedido.getId(),
            escapeHtml(pedido.getCliente()),
            escapeHtml(pedido.getProducto().getNombre()),
            pedido.getCantidad(),
            pedido.getTotal().toPlainString(),
            pedido.getFecha().format(formatter));
      }

      if (pedidos.isEmpty()) {
        out.println("<tr><td colspan=\"6\">Aún no hay pedidos.</td></tr>");
      }

      out.println("""
                        </tbody>
                      </table>
                    </body>
                    </html>
                    """);
    }
  }

  /**
   * Escapa caracteres especiales en una cadena para evitar vulnerabilidades XSS.
   *
   * @param value La cadena a escapar.
   * @return La cadena escapada.
   */
  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
