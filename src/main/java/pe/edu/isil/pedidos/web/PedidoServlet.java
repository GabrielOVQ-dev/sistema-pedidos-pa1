package pe.edu.isil.pedidos.web;

import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
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
import java.util.NoSuchElementException;
import pe.edu.isil.pedidos.domain.Pedido;
import pe.edu.isil.pedidos.domain.Producto;
import pe.edu.isil.pedidos.service.PedidoService;

/**
 * Servlet que maneja las solicitudes relacionadas con los pedidos.
 * Coordina la solicitud HTTP y delega toda la lógica de negocio a PedidoService.
 */
@WebServlet("/pedidos")
public class PedidoServlet extends HttpServlet {

  @EJB
  private PedidoService pedidoService;

  /**
   * Maneja las solicitudes GET: listado normal, mensajes de éxito por query param,
   * y carga del formulario de edición (?action=editar&id=...).
   */
  @Override
  protected void doGet(
          HttpServletRequest request,
          HttpServletResponse response)
          throws ServletException, IOException {

    String action = request.getParameter("action");

    if ("editar".equals(action)) {
      try {
        Long id = Long.valueOf(request.getParameter("id"));
        Pedido pedido = pedidoService.buscarPedido(id);
        renderizarPagina(response, null, null, pedido);
      } catch (NumberFormatException e) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        renderizarPagina(response, "ID de pedido inválido.", null, null);
      } catch (NoSuchElementException e) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        renderizarPagina(response, e.getMessage(), null, null);
      } catch (EJBException e) {
        manejarEJBException(response, e);
      }
      return;
    }

    String mensaje = null;
    if (request.getParameter("creado") != null) {
      mensaje = "Pedido #" + request.getParameter("creado") + " registrado correctamente.";
    } else if (request.getParameter("actualizado") != null) {
      mensaje = "Pedido #" + request.getParameter("actualizado") + " actualizado correctamente.";
    } else if (request.getParameter("eliminado") != null) {
      mensaje = "Pedido #" + request.getParameter("eliminado") + " eliminado correctamente.";
    }

    renderizarPagina(response, null, mensaje, null);
  }

  /**
   * Maneja las solicitudes POST: registrar, actualizar o eliminar un pedido,
   * según el parámetro "action". Esta es la solución base (hasta 17/20);
   * el reto avanzado agrega doPut()/doDelete() por separado.
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());

    String action = request.getParameter("action");
    if (action == null) {
      action = "registrar";
    }

    try {
      switch (action) {
        case "actualizar" -> {
          Long id = Long.valueOf(request.getParameter("id"));
          String cliente = request.getParameter("cliente");
          Long productoId = Long.valueOf(request.getParameter("productoId"));
          int cantidad = Integer.parseInt(request.getParameter("cantidad"));

          pedidoService.actualizarPedido(id, cliente, productoId, cantidad);
          response.sendRedirect(request.getContextPath() + "/pedidos?actualizado=" + id);
        }
        case "eliminar" -> {
          Long id = Long.valueOf(request.getParameter("id"));
          pedidoService.eliminarPedido(id);
          response.sendRedirect(request.getContextPath() + "/pedidos?eliminado=" + id);
        }
        default -> {
          String cliente = request.getParameter("cliente");
          Long productoId = Long.valueOf(request.getParameter("productoId"));
          int cantidad = Integer.parseInt(request.getParameter("cantidad"));

          Pedido pedido = pedidoService.registrarPedido(cliente, productoId, cantidad);
          response.sendRedirect(request.getContextPath() + "/pedidos?creado=" + pedido.getId());
        }
      }
    } catch (NumberFormatException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      renderizarPagina(response, "Datos inválidos: ID, producto o cantidad.", null, null);
    } catch (NoSuchElementException e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      renderizarPagina(response, e.getMessage(), null, null);
    } catch (IllegalArgumentException | IllegalStateException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      renderizarPagina(response, e.getMessage(), null, null);
    } catch (EJBException e) {
      manejarEJBException(response, e);
    }
  }

  /**
   * El contenedor EJB envuelve las excepciones no marcadas como "de aplicación"
   * (como IllegalArgumentException, IllegalStateException o NoSuchElementException)
   * dentro de un EJBException antes de propagarlas al llamador. Aquí se
   * "desenvuelve" la causa real para responder con el código HTTP correcto.
   */
  private void manejarEJBException(HttpServletResponse response, EJBException e) throws IOException {
    Throwable causa = e.getCause();

    if (causa instanceof NoSuchElementException nsee) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      renderizarPagina(response, nsee.getMessage(), null, null);
    } else if (causa instanceof IllegalArgumentException || causa instanceof IllegalStateException) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      renderizarPagina(response, causa.getMessage(), null, null);
    } else {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      renderizarPagina(response, "Ocurrió un error inesperado. Intenta de nuevo.", null, null);
    }
  }

  /**
   * Renderiza la página HTML: formulario de registro (o edición, si hay
   * pedidoEnEdicion), mensajes de éxito/error, y la lista de pedidos con
   * acciones de Editar/Eliminar.
   */
  private void renderizarPagina(
          HttpServletResponse response,
          String error,
          String mensaje,
          Pedido pedidoEnEdicion)
          throws IOException {
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
                        .exito { background: #e7ffe9; border: 1px solid #2a2; padding: 10px; margin: 16px 0; }
                        .nota { background: #f5f5f5; padding: 10px; margin: 16px 0; }
                        .acciones { display: flex; gap: 8px; align-items: center; }
                        .acciones button { padding: 6px 10px; font-size: 13px; }
                      </style>
                    </head>
                    <body>
                      <h1>Sistema de Pedidos</h1>
                      <p class=\"nota\">Flujo: Navegador → PedidoServlet → PedidoService (EJB) → JPA → H2.</p>
                    """);

      if (error != null) {
        out.printf("<div class=\"error\">%s</div>%n", escapeHtml(error));
      }
      if (mensaje != null) {
        out.printf("<div class=\"exito\">%s</div>%n", escapeHtml(mensaje));
      }

      if (pedidoEnEdicion != null) {
        out.printf("<h2>Editar pedido #%d</h2>%n", pedidoEnEdicion.getId());
        out.println("<form method=\"post\" action=\"pedidos\">");
        out.println("<input type=\"hidden\" name=\"action\" value=\"actualizar\">");
        out.printf("<input type=\"hidden\" name=\"id\" value=\"%d\">%n", pedidoEnEdicion.getId());
        out.println("<label>Cliente");
        out.printf(
                "<input name=\"cliente\" required maxlength=\"120\" value=\"%s\">%n",
                escapeHtml(pedidoEnEdicion.getCliente()));
        out.println("</label>");
        out.println("<label>Producto<select name=\"productoId\" required>");
        for (Producto producto : productos) {
          boolean seleccionado = producto.getId().equals(pedidoEnEdicion.getProducto().getId());
          out.printf(
                  "<option value=\"%d\" %s>%s - S/ %s - stock: %d</option>%n",
                  producto.getId(),
                  seleccionado ? "selected" : "",
                  escapeHtml(producto.getNombre()),
                  producto.getPrecio().toPlainString(),
                  producto.getStock());
        }
        out.println("</select></label>");
        out.println("<label>Cantidad");
        out.printf(
                "<input name=\"cantidad\" type=\"number\" min=\"1\" value=\"%d\" required>%n",
                pedidoEnEdicion.getCantidad());
        out.println("</label>");
        out.println("<button type=\"submit\">Guardar cambios</button>");
        out.println("</form>");
        out.println("<p><a href=\"pedidos\">Cancelar edición</a></p>");
      } else {
        out.println("""
                        <h2>Registrar pedido</h2>
                        <form method=\"post\" action=\"pedidos\">
                          <input type=\"hidden\" name=\"action\" value=\"registrar\">
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
                        """);
      }

      out.println("""
                      <h2>Pedidos registrados</h2>
                      <table>
                        <thead>
                          <tr>
                            <th>ID</th><th>Cliente</th><th>Producto</th><th>Cantidad</th><th>Total</th><th>Fecha</th><th>Acciones</th>
                          </tr>
                        </thead>
                        <tbody>
                    """);

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
      for (Pedido pedido : pedidos) {
        out.printf(
                "<tr><td>%d</td><td>%s</td><td>%s</td><td>%d</td><td>S/ %s</td><td>%s</td><td class=\"acciones\">%n",
                pedido.getId(),
                escapeHtml(pedido.getCliente()),
                escapeHtml(pedido.getProducto().getNombre()),
                pedido.getCantidad(),
                pedido.getTotal().toPlainString(),
                pedido.getFecha().format(formatter));

        out.printf(
                "<a href=\"pedidos?action=editar&id=%d\"><button type=\"button\">Editar</button></a>%n",
                pedido.getId());
        out.printf("""
            <form method=\"post\" action=\"pedidos\" style=\"display:inline\" onsubmit=\"return confirm('¿Eliminar este pedido?');\">
              <input type=\"hidden\" name=\"action\" value=\"eliminar\">
              <input type=\"hidden\" name=\"id\" value=\"%d\">
              <button type=\"submit\">Eliminar</button>
            </form>
            """, pedido.getId());

        out.println("</td></tr>");
      }

      if (pedidos.isEmpty()) {
        out.println("<tr><td colspan=\"7\">Aún no hay pedidos.</td></tr>");
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