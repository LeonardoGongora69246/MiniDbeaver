package minidbeaver.model;

public class ConexionConfig {

    public static final int PUERTO_MARIADB = 3306;

    // SSH
    private final String sshHost;
        private final int sshPuerto;
             private final String sshUsuario;
                 private final String sshPassword;

    // Base de datos (Servidor)
    private final String dbHost;
        private final String dbNombre;
            private final String dbUsuario;
                private final String dbPassword;

    public ConexionConfig(String sshHost, int sshPuerto, String sshUsuario, String sshPassword,
                          String dbHost, String dbNombre, String dbUsuario, String dbPassword) {
        this.sshHost = sshHost;
            this.sshPuerto = sshPuerto;
                this.sshUsuario = sshUsuario;
                this.sshPassword = sshPassword;
                    this.dbHost = dbHost;
                        this.dbNombre = dbNombre;
                            this.dbUsuario = dbUsuario;
                                this.dbPassword = dbPassword;
    }

    public String getSshHost() { return sshHost; }
        public int getSshPuerto() { return sshPuerto; }
        public String getSshUsuario() { return sshUsuario; }
        public String getSshPassword() { return sshPassword; }
        public String getDbHost() { return dbHost; }
        public String getDbNombre() { return dbNombre; }
        public String getDbUsuario() { return dbUsuario; }
        public String getDbPassword() { return dbPassword; }
}
