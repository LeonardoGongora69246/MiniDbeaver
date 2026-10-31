package minidbeaver.model;

public class SesionApp {

    private static final SesionApp INSTANCIA = new SesionApp();

    private final SshTunnelService tunel = new SshTunnelService();
        private ConexionConfig config;

    private SesionApp() { }

    public static SesionApp get() {
        return INSTANCIA;
    }

    //Abre tunel ssh
    public void iniciar(ConexionConfig cfg) throws Exception {
        cerrar();
        this.config = cfg;
        tunel.abrir(cfg);
    }

    //Puerto local donde se conecta MariaDB
    public int getPuertoLocal() { return tunel.getPuertoLocal(); }

    //Credenciales de inicio de sesion
    public ConexionConfig getConfig() { return config; }

    public void cerrar() {
        tunel.cerrar();
    }
}