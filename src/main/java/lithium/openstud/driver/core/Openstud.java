package lithium.openstud.driver.core;

import io.github.openunirest.http.HttpResponse;
import io.github.openunirest.http.JsonNode;
import io.github.openunirest.http.Unirest;
import io.github.openunirest.http.exceptions.UnirestException;
import lithium.openstud.driver.exceptions.OpenstudConnectionException;
import lithium.openstud.driver.exceptions.OpenstudEndpointNotReadyException;
import lithium.openstud.driver.exceptions.OpenstudInvalidPasswordException;
import lithium.openstud.driver.exceptions.OpenstudInvalidResponseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Openstud {
    private int maxTries;
    private String endpointAPI;
    private int connectionTimeout;
    private int socketTimeout;
    private volatile String token;
    private String studentPassword;
    private int studentID;
    private boolean isReady;
    private Logger logger;
    Openstud(String webEndpoint, int studentID, String studentPassword, Logger logger, int retryCounter, int connectionTimeout, int socketTimeout) {
        this.maxTries=retryCounter;
        this.endpointAPI=webEndpoint;
        this.connectionTimeout=connectionTimeout;
        this.socketTimeout=socketTimeout;
        this.studentID=studentID;
        this.studentPassword=studentPassword;
        this.logger=logger;
    }

    private void setToken(String token){
        this.token=token;
    }

    private String getToken(){
        return this.token;
    }

    private void log(Level lvl, String str){
        if (logger!=null) logger.log(lvl,str);
    }

    private void log(Level lvl, Object obj){
        if (logger!=null) logger.log(lvl,obj.toString());
    }

    public boolean isReady(){
        return isReady;
    }

    private int refreshToken(){
        try {
            Unirest.setTimeouts(connectionTimeout,socketTimeout);
            HttpResponse<JsonNode> jsonResponse = Unirest.post(endpointAPI+"/autenticazione").header("Accept","application/json")
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .field("key","r4g4zz3tt1").field("matricola",studentID).field("stringaAutenticazione",studentPassword).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) return -1;
            response=response.getJSONObject("object");
            if (!response.has("output")) return -1;
            setToken(response.getString("output"));
            if (response.has("esito")) {
                switch (response.getJSONObject("esito").getInt("flagEsito")) {
                    case -4:
                        return -1;
                    case -1:
                        return -1;
                    default:
                        return 0;
                }
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void login() throws OpenstudEndpointNotReadyException, OpenstudInvalidPasswordException, OpenstudConnectionException, OpenstudInvalidResponseException {
        int count=0;
        if (studentPassword==null) throw new OpenstudInvalidPasswordException("Password can't be empty");
        if (studentID==-1) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while(true){
            try {
                _login();
                break;
            } catch (OpenstudEndpointNotReadyException |OpenstudConnectionException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
            }
        }
    }

    private void _login() throws OpenstudInvalidPasswordException, OpenstudEndpointNotReadyException, OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            Unirest.setTimeouts(connectionTimeout,socketTimeout);
            HttpResponse<JsonNode> jsonResponse = Unirest.post(endpointAPI+"/autenticazione").header("Accept","application/json")
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .field("key","r4g4zz3tt1").field("matricola",studentID).field("stringaAutenticazione",studentPassword).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud response is not valid");
            response=response.getJSONObject("object");
            if (!response.has("output")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            setToken(response.getString("output"));
            if (response.has("esito")) {
                switch (response.getJSONObject("esito").getInt("flagEsito")) {
                    case -4:
                        throw new OpenstudInvalidResponseException("User is not enabled to use Infostud service.");
                    case -1:
                        throw new OpenstudInvalidPasswordException("Password not valid");
                    case 0:
                        break;
                    default:
                        throw new OpenstudEndpointNotReadyException("Infostud is not working as expected");
                }
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException("Unirest library can't process login, check internet connection.");
        }
        isReady=true;
    }

    public Isee getIsee() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        Isee isee;
        while(true){
            try {
                isee=_getIsee();
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return isee;
    }

    private Isee _getIsee() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI+"/contabilita/"+studentID+"/isee?ingresso="+getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud response is not valid");
            response=response.getJSONObject("object");
            if(!response.has("risultato")) throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response=response.getJSONObject("risultato");
            Isee res = new Isee();
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            for(String element : response.keySet()) {
                switch (element) {
                    case "value":
                        res.setValue(response.getDouble("value"));
                        break;
                    case "protocollo":
                        String protocol = response.getString("protocollo");
                        if (protocol == null || protocol.isEmpty()) return null;
                        res.setProtocol(response.getString("protocollo"));
                        break;
                    case "modificabile":
                        res.setEditable(response.getInt("modificabile")==1);
                        break;
                    case "dataOperazione":
                        String dateOperation = response.getString("dataOperazione");
                        if (!(dateOperation == null || dateOperation.isEmpty())) {
                            try {
                                res.setDateOperation(formatter.parse(response.getString("dataOperazione")));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "data":
                        String dateDeclaration = response.getString("data");
                        if (!(dateDeclaration == null || dateDeclaration.isEmpty())) {
                            try {
                                res.setDateDeclaration(formatter.parse(response.getString("data")));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                }
            }
            return  res;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException("Unirest library can't get isee, check internet connection.");
        }
    }


    public Student getInfoStudent() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        Student st;
        while(true){
            try {
                st=_getInfoStudent();
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return st;
    }

    private Student _getInfoStudent() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI+"/studente/"+studentID+"?ingresso="+getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response=response.getJSONObject("object");
            if(!response.has("ritorno")) throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response=response.getJSONObject("ritorno");
            Student st = new Student();
            st.setStudentID(studentID);
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            for(String element : response.keySet()) {
                switch (element) {
                    case "codiceFiscale":
                        st.setCF(response.getString("codiceFiscale"));
                        break;
                    case "cognome":
                        st.setLastName(response.getString("cognome"));
                        break;
                    case "nome":
                        st.setFirstName(response.getString("nome"));
                        break;
                    case "dataDiNascita":
                        String dateBirth = response.getString("dataDiNascita");
                        if (!(dateBirth == null || dateBirth.isEmpty())) {
                            try {
                                st.setBirthDate(formatter.parse(response.getString("dataDiNascita")));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "comuneDiNasciata":
                        st.setBirthCity(response.getString("comuneDiNasciata"));
                        break;
                    case "luogoDiNascita":
                        st.setBirthPlace(response.getString("luogoDiNascita"));
                        break;
                    case "annoCorso":
                        st.setCourseYear(response.getString("annoCorso"));
                        break;
                    case "primaIscr":
                        st.setFirstEnrollment(response.getString("primaIscr"));
                        break;
                    case "ultIscr":
                        st.setLastEnrollment(response.getString("ultIscr"));
                        break;
                    case "facolta":
                        st.setDepartmentName(response.getString("facolta"));
                        break;
                    case "nomeCorso":
                        st.setCourseName(response.getString("nomeCorso"));
                        break;
                    case "annoAccaAtt":
                        st.setAcademicYear(response.getInt("annoAccaAtt"));
                        break;
                    case "codCorso":
                        st.setCodeCourse(response.getInt("codCorso"));
                        break;
                    case "tipoStudente":
                        st.setTypeStudent(response.getInt("tipoStudente"));
                        break;
                    case "tipoIscrizione":
                        st.setStudentStatus(response.getString("tipoIscrizione"));
                        break;
                    case "isErasmus":
                        st.setErasmus(response.getBoolean("isErasmus"));
                        break;
                    case "nazioneNascita":
                        st.setNation(response.getString("nazioneNascita"));
                        break;
                    case "creditiTotali":
                        st.setCfu(response.getInt("creditiTotali"));
                        break;
                    case "indiMailIstituzionale":
                        st.setEmail(response.getString("indiMailIstituzionale"));
                    case "sesso":
                        st.setGender(response.getString("sesso"));
                        break;
                    case "annoAccaCors":
                        st.setAcademicYearCourse(response.getInt("annoAccaCors"));
                        break;
                    case "cittadinanza":
                        st.setCitizenship(response.getString("cittadinanza"));
                        break;
                }
            }
            return  st;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException("Unirest library can't get isee, check internet connection.");
        }
    }


    public List<ExamDoable> getExamsDoable() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        List<ExamDoable> exams;
        while(true){
            try {
                exams=_getExamsDoable();
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return exams;
    }

    private List<ExamDoable> _getExamsDoable() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI + "/studente/" + studentID + "/insegnamentisostenibili?ingresso=" + getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (!response.has("ritorno"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("ritorno");
            List<ExamDoable> list = new LinkedList<>();
            if (!response.has("esami")) return null;
            JSONArray array = response.getJSONArray("esami");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ExamDoable exam = new ExamDoable();
                for (String element : obj.keySet()) {
                    switch (element) {
                        case "codiceInsegnamento":
                            exam.setExamCode(obj.getString("codiceInsegnamento"));
                            break;
                        case "codiceModuloDidattico":
                            exam.setModuleCode(obj.getString("codiceModuloDidattico"));
                            break;
                        case "codiceCorsoInsegnamento":
                            exam.setCourseCode(obj.getString("codiceCorsoInsegnamento"));
                            break;
                        case "cfu":
                            exam.setCfu(obj.getInt("cfu"));
                            break;
                        case "descrizione":
                            exam.setDescription(obj.getString("descrizione"));
                            break;
                        case "ssd":
                            exam.setSsd(obj.getString("ssd"));
                            break;
                    }
                }
                list.add(exam);
            }
            return list;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    public List<ExamPassed> getExamsPassed() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        List<ExamPassed> exams;
        while(true){
            try {
                exams=_getExamsPassed();
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return exams;
    }

    private List<ExamPassed> _getExamsPassed() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI + "/studente/" + studentID + "/esami?ingresso=" + getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (!response.has("ritorno"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("ritorno");
            List<ExamPassed> list = new LinkedList<>();
            if (!response.has("esami")) return null;
            JSONArray array = response.getJSONArray("esami");
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ExamPassed exam = new ExamPassed();
                for (String element : obj.keySet()) {
                    switch (element) {
                        case "codiceInsegnamento":
                            exam.setExamCode(obj.getString("codiceInsegnamento"));
                            break;
                        case "cfu":
                            exam.setCfu(obj.getInt("cfu"));
                            break;
                        case "descrizione":
                            exam.setDescription(obj.getString("descrizione"));
                            break;
                        case "ssd":
                            exam.setSsd(obj.getString("ssd"));
                            break;
                        case "data":
                            String dateBirth = obj.getString("data");
                            if (!(dateBirth == null || dateBirth.isEmpty())) {
                                try {
                                    exam.setDate(formatter.parse(dateBirth));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case "annoAcca":
                            exam.setYear(obj.getInt("annoAcca"));
                            break;
                        case "esito":
                            JSONObject esito=obj.getJSONObject("esito");
                            if(esito.has("valoreNominale")) exam.setNominalResult(esito.getString("valoreNominale"));
                            if(esito.has("valoreNonNominale") && !esito.isNull("valoreNonNominale")) exam.setResult(esito.getInt("valoreNonNominale"));
                            break;
                    }
                }
                list.add(exam);
            }
            return list;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    public List<ExamReservation> getActiveReservations() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        List<ExamReservation> reservations;
        while(true){
            try {
                reservations=_getActiveReservations();
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return reservations;
    }

    private List<ExamReservation> _getActiveReservations() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI + "/studente/" + studentID + "/prenotazioni?ingresso=" + getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (!response.has("ritorno"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("ritorno");
            List<ExamReservation> list = new LinkedList<>();
            if (!response.has("appelli")) return null;
            JSONArray array = response.getJSONArray("appelli");
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            extractReservations(list, array, formatter);
            return list;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    private void extractReservations(List<ExamReservation> list, JSONArray array, SimpleDateFormat formatter) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            ExamReservation res = new ExamReservation();
            for (String element : obj.keySet()) {
                switch (element) {
                    case "codIdenVerb":
                        res.setReportID(obj.getInt("codIdenVerb"));
                        break;
                    case "codAppe":
                        res.setSessionID(obj.getInt("codAppe"));
                        break;
                    case "codCorsoStud":
                        res.setCourseCode(Integer.parseInt(obj.getString("codCorsoStud")));
                        break;
                    case "descrizione":
                        res.setExamSubject(obj.getString("descrizione"));
                        break;
                    case "descCorsoStud":
                        res.setCourseDescription(obj.getString("descCorsoStud"));
                        break;
                    case "crediti":
                        res.setCfu(obj.getInt("crediti"));
                        break;
                    case "docente":
                        res.setTeacher(obj.getString("docente"));
                        break;
                    case "annoAcca":
                        res.setYearCourse(obj.getString("annoAcca"));
                        break;
                    case "facolta":
                        res.setDepartment(obj.getString("facolta"));
                        break;
                    case "numeroPrenotazione":
                        if (obj.isNull("numeroPrenotazione")) break;
                        res.setReservationNumber(obj.getInt("numeroPrenotazione"));
                        break;
                    case "ssd":
                        if (obj.isNull("ssd")) break;
                        res.setSsd(obj.getString("ssd"));
                        break;
                    case "dataprenotazione":
                        if (obj.isNull("dataprenotazione")) break;
                        String reservationDate = obj.getString("dataprenotazione");
                        if (!(reservationDate == null || reservationDate.isEmpty())) {
                            try {
                                res.setReservationDate(formatter.parse(reservationDate));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "note":
                        res.setNote(obj.getString("note"));
                        break;
                    case "dataAppe":
                        String examDate = obj.getString("dataAppe");
                        if (!(examDate == null || examDate.isEmpty())) {
                            try {
                                res.setExamDate(formatter.parse(examDate));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "dataInizioPrenotazione":
                        if(obj.isNull("dataInizioPrenotazione")) break;
                        String startDate = obj.getString("dataInizioPrenotazione");
                        if (!(startDate == null || startDate.isEmpty())) {
                            try {
                                res.setStartDate(formatter.parse(startDate));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "dataFinePrenotazione":
                        if(obj.isNull("dataFinePrenotazione")) break;
                        String endDate = obj.getString("dataFinePrenotazione");
                        if (!(endDate == null || endDate.isEmpty())) {
                            try {
                                res.setEndDate(formatter.parse(endDate));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case "SiglaModuloDidattico":
                        if(!obj.isNull("SiglaModuloDidattico")) res.setModule(obj.getString("SiglaModuloDidattico"));
                        break;
                }
            }
            list.add(res);
        }
    }

    public List<ExamReservation> getAvailableReservations(ExamDoable exam, Student student) throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady()) return null;
        int count=0;
        List<ExamReservation> reservations;
        while(true){
            try {
                reservations=_getAvailableReservations(exam, student);
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return reservations;
    }

    private List<ExamReservation> _getAvailableReservations(ExamDoable exam, Student student) throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI + "/appello/ricerca?ingresso=" + getToken()+ "&tipoRicerca="+4+"&criterio="+exam.getModuleCode()+
                            "&codiceCorso="+exam.getCourseCode()+"&annoAccaAuto="+student.getAcademicYearCourse()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (!response.has("ritorno")) throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("ritorno");
            List<ExamReservation> list = new LinkedList<>();
            if (!response.has("appelli")) return null;
            JSONArray array = response.getJSONArray("appelli");
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            extractReservations(list, array, formatter);
            return list;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    public Pair<Integer,String> insertReservation(ExamReservation res) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        if (!isReady()) return null;
        int count=0;
        Pair<Integer,String> pr;
        while(true){
            try {
                pr =_insertReservation(res);
                if(((ImmutablePair<Integer, String>) pr).left==-1 && ((ImmutablePair<Integer, String>) pr).right==null) {
                    if (!(++count == maxTries)) continue;
                }
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        if(((ImmutablePair<Integer, String>) pr).left==-1 && ((ImmutablePair<Integer, String>) pr).right==null) return null;
        return pr;
    }

    private ImmutablePair<Integer,String> _insertReservation(ExamReservation res) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.post(endpointAPI + "/prenotazione/" + res.getReportID() + "/" + res.getSessionID()
                    + "/" + res.getCourseCode() + "?ingresso=" + getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            String url = null;
            int flag = -1;
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (response.has("esito")) {
                if (response.getJSONObject("esito").has("flagEsito")) {
                    flag = response.getJSONObject("esito").getInt("flagEsito");
                }
            }
            else throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            if (!response.isNull("url") && response.has("url")) url = response.getString("url");
            return new ImmutablePair<>(flag, url);
        } catch (UnirestException e){
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    public int deleteReservation(ExamReservation res) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        if (!isReady() || res.getReservationNumber()==-1) return -1;
        int count=0;
        int ret;
        while(true){
            try {
                ret =_deleteReservation(res);
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return ret;
    }

    private int _deleteReservation(ExamReservation res) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.delete(endpointAPI + "/prenotazione/" + res.getReportID() + "/" + res.getSessionID()
                    + "/" + studentID + "/"+res.getReservationNumber()+"?ingresso=" + getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            log(Level.INFO,response);
            int flag = -1;
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            response = response.getJSONObject("object");
            if (response.has("esito")) {
                if (response.getJSONObject("esito").has("flagEsito")) {
                    flag = response.getJSONObject("esito").getInt("flagEsito");
                }
            }
            else throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            return flag;
        } catch (UnirestException e){
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }

    public byte[] getPdf(ExamReservation reservation) throws OpenstudConnectionException, OpenstudInvalidResponseException {
        if (!isReady() || reservation==null) return null;
        int count=0;
        byte[] pdf;
        while(true){
            try {
                pdf=_getPdf(reservation);
                break;
            } catch (OpenstudConnectionException|OpenstudInvalidResponseException e) {
                if (++count == maxTries) {
                    log(Level.SEVERE,e);
                    throw e;
                }
                if (refreshToken()==-1) {
                    log(Level.SEVERE,"FAILED REFRESH!! :"+e.toString());
                    throw e;
                }
            }
        }
        return pdf;
    }

    private byte[] _getPdf(ExamReservation res) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        try {
            HttpResponse<JsonNode> jsonResponse = Unirest.get(endpointAPI+"/prenotazione/"+res.getReportID()+"/"+res.getSessionID()+"/"
                            +studentID+"/pdf?ingresso="+getToken()).asJson();
            JSONObject response = new JSONObject(jsonResponse.getBody());
            if (!response.has("object")) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            JSONObject obj=response.getJSONObject("object");
            if(!obj.has("risultato") || obj.isNull("risultato")) return null;
            obj=obj.getJSONObject("risultato");
            if(!obj.has("byte") || obj.isNull("byte")) return null;
            JSONArray byteArray= obj.getJSONArray("byte");
            byte[] pdf = new byte[byteArray.length()];
            for(int i=0;i<byteArray.length();i++) pdf[i] = (byte) byteArray.getInt(i);
            log(Level.INFO,"Found PDF made of "+pdf.length+" bytes \n");
            return  pdf;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new OpenstudConnectionException(e);
        }
    }


}