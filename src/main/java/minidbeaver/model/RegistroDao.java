package minidbeaver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * INTEGRANTE 4:
 * CRUD genérico (columnas dinámicas) sobre las tablas descargadas en SQLite.
 * No conoce ningún control de JavaFX (MVC).
 *
 * Para identificar cada registro se usa el rowid interno de SQLite, así
 * funciona igual con claves primarias simples, compuestas o sin clave.
 */
public class RegistroDao {

    private final SqliteManager sqlite = new SqliteManager();

    // ---------------------------------------------------------------
    // Clases de datos
    // ---------------------------------------------------------------

    public static class Columna {
        private final String nombre;
        private final String tipo;
        private final boolean obligatoria;
        private final boolean primaryKey;

        public Columna(String nombre, String tipo, boolean obligatoria, boolean primaryKey) {
            this.nombre = nombre;
            this.tipo = tipo == null ? "" : tipo.toUpperCase();
            this.obligatoria = obligatoria;
            this.primaryKey = primaryKey;
        }

        public String getNombre() { return nombre; }
        public String getTipo() { return tipo; }
        public boolean isObligatoria() { return obligatoria; }
        public boolean isPrimaryKey() { return primaryKey; }
        public boolean isBlob() { return tipo.contains("BLOB"); }
        public boolean isEntero() { return tipo.contains("INT"); }
    }

    public static class Fila {
        private final long rowid;
        private final List<Object> valores;

        public Fila(long rowid, List<Object> valores) {
            this.rowid = rowid;
            this.valores = valores;
        }

        public long getRowid() { return rowid; }
        public List<Object> getValores() { return valores; }
    }

    public static class Resultado {
        private final List<Columna> columnas;
        private final List<Fila> filas;

        public Resultado(List<Columna> columnas, List<Fila> filas) {
            this.columnas = columnas;
            this.filas = filas;
        }

        public List<Columna> getColumnas() { return columnas; }
        public List<Fila> getFilas() { return filas; }
    }

    // ---------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------

    private String citar(String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) {
            throw new SQLException("Nombre de tabla o columna vacío");
        }
        return "\"" + nombre.replace("\"", "\"\"") + "\"";
    }

    private List<Columna> leerColumnas(Connection con, String tabla) throws SQLException {
        List<Columna> columnas = new ArrayList<>();

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + citar(tabla) + ")")) {

            while (rs.next()) {
                columnas.add(new Columna(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1,
                        rs.getInt("pk") > 0));
            }
        }

        if (columnas.isEmpty()) {
            throw new SQLException("La tabla '" + tabla + "' no existe en SQLite");
        }
        return columnas;
    }

    /** Convierte el texto escrito en el formulario al tipo de la columna. */
    private Object convertir(String texto, Columna col) throws SQLException {
        if (texto == null || texto.isBlank()) {
            // Texto obligatorio vacío se guarda como "", el resto como NULL
            boolean esTexto = !col.isEntero() && !col.getTipo().contains("REAL")
                    && !col.getTipo().contains("NUMERIC") && !col.isBlob();
            return (esTexto && col.isObligatoria()) ? "" : null;
        }

        String t = texto.trim();
        try {
            if (col.isEntero()) {
                return Long.parseLong(t);
            }
            if (col.getTipo().contains("REAL")) {
                return Double.parseDouble(t);
            }
            if (col.getTipo().contains("NUMERIC")) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(t);
                }
            }
        } catch (NumberFormatException e) {
            throw new SQLException("El valor '" + t + "' no es válido para la columna "
                    + col.getNombre() + " (" + col.getTipo() + ")");
        }
        return texto;
    }

    private Map<String, Columna> indexar(List<Columna> columnas) {
        Map<String, Columna> mapa = new HashMap<>();
        for (Columna c : columnas) {
            mapa.put(c.getNombre(), c);
        }
        return mapa;
    }

    // ---------------------------------------------------------------
    // CONSULTAR
    // ---------------------------------------------------------------

    public Resultado consultar(String tabla) throws SQLException {
        try (Connection con = sqlite.conectar()) {

            List<Columna> columnas = leerColumnas(con, tabla);
            List<Fila> filas = new ArrayList<>();

            String sql = "SELECT rowid, * FROM " + citar(tabla);

            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                while (rs.next()) {
                    long rowid = rs.getLong(1);
                    List<Object> valores = new ArrayList<>(columnas.size());

                    for (int i = 0; i < columnas.size(); i++) {
                        valores.add(rs.getObject(i + 2));
                    }
                    filas.add(new Fila(rowid, valores));
                }
            }
            return new Resultado(columnas, filas);
        }
    }

    // ---------------------------------------------------------------
    // BUSCAR (Read con filtro)
    // ---------------------------------------------------------------

    public Resultado buscar(String tabla, String columna, String texto) throws SQLException {
        try (Connection con = sqlite.conectar()) {
            List<Columna> columnas = leerColumnas(con, tabla);

            boolean existe = columnas.stream().anyMatch(c -> c.getNombre().equals(columna));
            if (!existe) {
                throw new SQLException("Columna desconocida: " + columna);
            }

            String sql = "SELECT rowid, * FROM " + citar(tabla)
                    + " WHERE CAST(" + citar(columna) + " AS TEXT) LIKE ?";

            List<Fila> filas = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, "%" + texto.trim() + "%");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long rowid = rs.getLong(1);
                        List<Object> valores = new ArrayList<>(columnas.size());

                        for (int i = 0; i < columnas.size(); i++) {
                            valores.add(rs.getObject(i + 2));
                        }
                        filas.add(new Fila(rowid, valores));
                    }
                }
            }
            return new Resultado(columnas, filas);
        }
    }

    // ---------------------------------------------------------------
    // INSERTAR
    // ---------------------------------------------------------------

    public void insertar(String tabla, List<String> nombres, List<String> textos) throws SQLException {
        if (nombres.size() != textos.size()) {
            throw new SQLException("Los valores no coinciden con las columnas");
        }

        try (Connection con = sqlite.conectar()) {
            Map<String, Columna> mapa = indexar(leerColumnas(con, tabla));

            String sql;
            if (nombres.isEmpty()) {
                sql = "INSERT INTO " + citar(tabla) + " DEFAULT VALUES";
            } else {
                StringBuilder cols = new StringBuilder();
                StringBuilder marcas = new StringBuilder();
                for (int i = 0; i < nombres.size(); i++) {
                    if (i > 0) {
                        cols.append(", ");
                        marcas.append(", ");
                    }
                    cols.append(citar(nombres.get(i)));
                    marcas.append("?");
                }
                sql = "INSERT INTO " + citar(tabla) + " (" + cols + ") VALUES (" + marcas + ")";
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < nombres.size(); i++) {
                    Columna col = mapa.get(nombres.get(i));
                    if (col == null) {
                        throw new SQLException("Columna desconocida: " + nombres.get(i));
                    }
                    ps.setObject(i + 1, convertir(textos.get(i), col));
                }
                ps.executeUpdate();
            }
        }
    }

    // ---------------------------------------------------------------
    // ACTUALIZAR
    // ---------------------------------------------------------------

    public int actualizar(String tabla, long rowid, List<String> nombres, List<String> textos)
            throws SQLException {

        if (nombres.isEmpty() || nombres.size() != textos.size()) {
            throw new SQLException("No hay valores para actualizar");
        }

        try (Connection con = sqlite.conectar()) {
            Map<String, Columna> mapa = indexar(leerColumnas(con, tabla));

            StringBuilder sets = new StringBuilder();
            for (int i = 0; i < nombres.size(); i++) {
                if (i > 0) sets.append(", ");
                sets.append(citar(nombres.get(i))).append(" = ?");
            }

            String sql = "UPDATE " + citar(tabla) + " SET " + sets + " WHERE rowid = ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < nombres.size(); i++) {
                    Columna col = mapa.get(nombres.get(i));
                    if (col == null) {
                        throw new SQLException("Columna desconocida: " + nombres.get(i));
                    }
                    ps.setObject(i + 1, convertir(textos.get(i), col));
                }
                ps.setLong(nombres.size() + 1, rowid);
                return ps.executeUpdate();
            }
        }
    }

    // ---------------------------------------------------------------
    // ELIMINAR
    // ---------------------------------------------------------------

    public int eliminar(String tabla, long rowid) throws SQLException {
        String sql = "DELETE FROM " + citar(tabla) + " WHERE rowid = ?";

        try (Connection con = sqlite.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, rowid);
            return ps.executeUpdate();
        }
    }
}