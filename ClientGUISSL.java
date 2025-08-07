import javax.net.ssl.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

//MUTONKOLE KAYUMBA GUEZ
//MWANZA NKONTO ASTRID
//MANONGO VICTOR MARCEL
//ILUNGA WA ILUNGA JACQUIE
//ILUNGA BANZA SARAH
/**
 * Client messagerie SSL/TLS avec interface graphique Swing.
 *
 * Fonctionnalités :
 * - Saisie adresse IP et pseudo utilisateur
 * - Connexion sécurisée via SSLSocket
 * - Liste dynamique des utilisateurs connectés
 * - Gestion multi-fenêtres de chat sans doublons
 * - Envoi et réception des messages texte
 * - Bouton déconnexion propre
 */
public class ClientGUISSL extends JFrame {

    // Composants GUI
    private JTextField txtIP;
    private JTextField txtPseudo;
    private JButton btnConnecter;
    private JButton btnDeconnecter;

    private DefaultListModel<String> modelContacts;
    private JList<String> listContacts;
    private JTextArea areaConsole;

    // Réseau SSL
    private SSLSocket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String nomUtilisateur;
    private final int PORT = 12345;

    // Fenêtres de chat ouvertes par pseudo contact
    private final Map<String, FenetreDiscussion> fenetresChats = new HashMap<>();

    /**
     * Constructeur qui initialise l'interface graphique.
     */
    public ClientGUISSL() {
        super("Client Messagerie SSL");

        // --- Panel connexion : saisie IP, pseudo, boutons ---
        JPanel panelConn = new JPanel(new FlowLayout());
        panelConn.add(new JLabel("Adresse IP serveur :"));
        txtIP = new JTextField("127.0.0.1", 12);
        panelConn.add(txtIP);

        panelConn.add(new JLabel("Pseudo :"));
        txtPseudo = new JTextField(10);
        panelConn.add(txtPseudo);

        btnConnecter = new JButton("Connecter");
        panelConn.add(btnConnecter);

        btnDeconnecter = new JButton("Déconnecter");
        btnDeconnecter.setEnabled(false);  // désactivé au départ
        panelConn.add(btnDeconnecter);

        // --- Liste des contacts connectés ---
        modelContacts = new DefaultListModel<>();
        listContacts = new JList<>(modelContacts);
        listContacts.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollContacts = new JScrollPane(listContacts);
        scrollContacts.setPreferredSize(new Dimension(200, 300));

        JPanel panelContacts = new JPanel(new BorderLayout());
        panelContacts.setBorder(BorderFactory.createTitledBorder("Contacts connectés"));
        panelContacts.add(scrollContacts, BorderLayout.CENTER);

        // --- Console logs/messages généraux ---
        areaConsole = new JTextArea(10, 40);
        areaConsole.setEditable(false);
        JScrollPane scrollConsole = new JScrollPane(areaConsole);

        // --- Layout principal ---
        setLayout(new BorderLayout());
        add(panelConn, BorderLayout.NORTH);
        add(panelContacts, BorderLayout.WEST);
        add(scrollConsole, BorderLayout.CENTER);

        // Configuration fenêtre
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Actions boutons
        btnConnecter.addActionListener(e -> connecterServeur());
        btnDeconnecter.addActionListener(e -> deconnecterServeur());

        // Double-clic ouvre fenêtre chat
        listContacts.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String pseudoContact = listContacts.getSelectedValue();
                    if (pseudoContact != null && !pseudoContact.equals(nomUtilisateur)) {
                        ouvrirFenetreChat(pseudoContact);
                    }
                }
            }
        });
    }

    /**
     * Tente la connexion au serveur SSL dans un thread séparé.
     */
    private void connecterServeur() {
        String ip = txtIP.getText().trim();
        String pseudo = txtPseudo.getText().trim();

        if (ip.isEmpty() || pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Veuillez saisir l'adresse IP et un pseudo.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Désactiver bouton Connecter pour éviter double clic
        setConnectionUIState(false);

        new Thread(() -> {
            try {
                // Chargement du truststore client
                KeyStore trustStore = KeyStore.getInstance("JKS");
                try (InputStream ts = new FileInputStream("clienttruststore.jks")) {
                    trustStore.load(ts, "123456".toCharArray()); // Adapter mot de passe si besoin
                }

                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(trustStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                SSLSocketFactory ssf = sslContext.getSocketFactory();
                socket = (SSLSocket) ssf.createSocket(ip, PORT);
                socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());

                socket.startHandshake();

                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // Boucle d'authentification
                String reponse;
                while ((reponse = in.readLine()) != null) {
                    System.out.println("Reçu du serveur (auth) : " + reponse);

                    if ("SUBMITNAME".equals(reponse)) {
                        out.println(pseudo);
                    } else if ("NAMEINUSE".equals(reponse)) {
                        showErrorDialog("Pseudo déjà utilisé, choisissez-en un autre.");
                        closeConnection();
                        setConnectionUIState(true);
                        return;
                    } else if (reponse.startsWith("NAMEACCEPTED")) {
                        nomUtilisateur = pseudo;
                        appendConsole("Connecté en tant que " + nomUtilisateur);
                        setConnectionUIState(true, true);
                        break;
                    }
                }

                // Lancer thread écoute messages serveur
                new Thread(this::ecouterServeur).start();

            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Erreur de connexion : " + e.getMessage());
                setConnectionUIState(true);
            }
        }).start();
    }

    /**
     * Ecoute en boucle les messages du serveur et agit en conséquence.
     */
    private void ecouterServeur() {
        try {
            String ligne;
            while ((ligne = in.readLine()) != null) {
                System.out.println("Reçu du serveur: " + ligne);

                if (ligne.startsWith("USER_LIST ")) {
                    String[] users = ligne.substring(10).split(" ");
                    SwingUtilities.invokeLater(() -> {
                        modelContacts.clear();
                        for (String user : users) {
                            modelContacts.addElement(user);
                        }
                    });
                } else if (ligne.startsWith("MSG ")) {
                    String reste = ligne.substring(4);
                    int sep = reste.indexOf(' ');
                    if (sep > 0) {
                        String expediteur = reste.substring(0, sep);
                        String msg = reste.substring(sep + 1);
                        SwingUtilities.invokeLater(() -> {
                            ouvrirFenetreChat(expediteur).afficherMessage(expediteur + " : " + msg);
                        });
                    }
                } else {
                    System.out.println("Message non reconnu du serveur : " + ligne);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            appendConsole("Déconnecté du serveur.");
            resetUIAfterDisconnect();
        } finally {
            closeConnection();
            resetUIAfterDisconnect();
        }
    }

    /**
     * Ouvre ou remet au premier plan une fenêtre de chat avec un contact donné.
     */
    private FenetreDiscussion ouvrirFenetreChat(String pseudoContact) {
        FenetreDiscussion fen = fenetresChats.get(pseudoContact);
        if (fen == null) {
            fen = new FenetreDiscussion(pseudoContact, this);
            fenetresChats.put(pseudoContact, fen);
            fen.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    fenetresChats.remove(pseudoContact);
                }
            });
            fen.setVisible(true);
        } else {
            fen.toFront();
            fen.requestFocus();
        }
        return fen;
    }

    /**
     * Envoi un message texte au serveur avec format "MSG destinataire message".
     * @param destinataire pseudo destinataire
     * @param texte message à envoyer
     */
    public void envoyerMessage(String destinataire, String texte) {
        if (out != null) {
            out.println("MSG " + destinataire + " " + texte);
            appendConsole("Vous à " + destinataire + " : " + texte);
        } else {
            appendConsole("Impossible d'envoyer : non connecté.");
        }
    }

    /**
     * Déconnexion propre : informe le serveur puis ferme la connexion et nettoie la GUI.
     */
    private void deconnecterServeur() {
        if (out != null) {
            out.println("LOGOUT");
        }
        closeConnection();
        appendConsole("Déconnecté.");
        resetUIAfterDisconnect();

        // Fermer toutes fenêtres de chat ouvertes
        fenetresChats.values().forEach(Window::dispose);
        fenetresChats.clear();
    }

    /**
     * Ferme les flux et la socket proprement.
     */
    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        out = null;
        in = null;
    }

    /**
     * Met à jour l'interface selon l'état de connexion.
     * @param enableConnectButton active/désactive le bouton Connecter
     * @param connected true si connecté, false si déconnecté
     */
    private void setConnectionUIState(boolean enableConnectButton, boolean connected) {
        SwingUtilities.invokeLater(() -> {
            btnConnecter.setEnabled(enableConnectButton);
            btnDeconnecter.setEnabled(connected);
            txtIP.setEnabled(!connected);
            txtPseudo.setEnabled(!connected);
        });
    }

    /**
     * Surcharge pour état déconnecté (connected = false)
     */
    private void setConnectionUIState(boolean enableConnectButton) {
        setConnectionUIState(enableConnectButton, false);
    }

    /**
     * Affiche un message dans la console texte de l'interface.
     */
    private void appendConsole(String msg) {
        SwingUtilities.invokeLater(() -> areaConsole.append(msg + "\n"));
    }

    /**
     * Affiche une boîte de dialogue d'erreur dans le thread Swing.
     */
    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Erreur", JOptionPane.ERROR_MESSAGE));
    }

    /**
     * Remet à zéro l'interface après déconnexion.
     */
    private void resetUIAfterDisconnect() {
        setConnectionUIState(true, false);
        SwingUtilities.invokeLater(() -> modelContacts.clear());
    }

    /**
     * Retourne le pseudo utilisateur connecté.
     */
    public String getNomUtilisateur() {
        return nomUtilisateur;
    }

    /**
     * Méthode main, démarre la GUI sur le thread Swing.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUISSL::new);
    }
}
