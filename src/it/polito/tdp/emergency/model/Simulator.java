package it.polito.tdp.emergency.model;

import java.util.PriorityQueue;

import org.omg.CORBA.TIMEOUT;

import it.polito.tdp.emergency.model.Event.EventType;
import it.polito.tdp.emergency.model.Patient.PatientStatus;

public class Simulator {

	// Simulation parameters

	private int NS; // number of studios

	private int DURATION_TRIAGE = 5 * 60;
	private int DURATION_WHITE = 10 * 60;
	private int DURATION_YELLOW = 15 * 60;
	private int DURATION_RED = 30 * 60;

	private int WHITE_TIMEOUT = 30 * 60;
	private int YELLOW_TIMEOUT = 30 * 60;
	private int RED_TIMEOUT = 60 * 60;

	// World model
	private PriorityQueue<Patient> waitingRoom;		//mi estrae sempre il paziente con precedenza maggiore, dove la precedenza è in base al codice e il tempo di arrivo
	private int occupiedStudios = 0;				//devo tener traccia di quanti studi medici sono occupati

	// Measures of Interest
	private int patientsTreated = 0;				
	private int patientsDead = 0;
	private int patientsAbandoned = 0;

	// Event queue
	private PriorityQueue<Event> queue;				//coda degli eventi

	public Simulator(int NS) {
		this.NS = NS;
		this.queue = new PriorityQueue<>();									//coda degli eventi a vuoto
		this.waitingRoom = new PriorityQueue<>(new PatientComparator());
	}
//aggiungi il paziente ad un determinato istante di tempo
	public void addPatient(Patient patient, int time) {
		//devo assegnare lo stato new e prevedere che terminato il triage gli viene assegnato un codice colore
		patient.setStatus(PatientStatus.NEW);
		
		//schedulo l'evento per quando il paziente esce dal triage
		Event e = new Event(patient, time+ DURATION_TRIAGE, EventType.TRIAGE);
		
		//aggiungo l'evento alla coda
		queue.add(e);	
	}

	public void run() {
		while (!queue.isEmpty()) {
			Event e = queue.poll();
			System.out.println(e);

			switch (e.getType()) {
			case TRIAGE:
				processTriageEvent(e);
				break;
			case TIMEOUT:
				processTimeoutEvent(e);
				break;
			case FREE_STUDIO:
				processFreeStudioEvent(e);
				break;
			}
		}
	}

	/**
	 * A patient finished treatment. The studio is freed, and a new patient is
	 * called in.
	 * 
	 * @param e
	 */
	private void processFreeStudioEvent(Event e) {
//un paziente libera lo studio e devo chiamare il prossimo paziente
//paziente termina la cura
		Patient p = e.getPatient();
		
		this.patientsTreated++;
		
		p.setStatus(PatientStatus.OUT);
		this.occupiedStudios--;				//si libera lo studio
		
		//chiamo il prossimo dalla waiting room
		Patient next = waitingRoom.poll();
		
		
		if(next != null){		//se c'è qualcuno in lista d'attesa allora lo chiamo ed occupo lo studio
			this.occupiedStudios++;
			int duration = 0;
			
			if(next.getStatus()==PatientStatus.WHITE)
				duration = DURATION_WHITE;
			else if (next.getStatus()==PatientStatus.YELLOW)
				duration = DURATION_YELLOW;
			else if (next.getStatus() == PatientStatus.RED)
				duration = DURATION_RED;
			
			next.setStatus(PatientStatus.TREATING);
			//metto l'evento in coda per quando il paziente se ne va dopo la cura
			
			//eliminare il timeout dalla coda di eventi oppure la tolgo nello switch case
			
			queue.add(new Event(next, e.getTime()+duration, EventType.FREE_STUDIO));	
		}
	}

	private void processTimeoutEvent(Event e) {
		Patient p = e.getPatient();
		
		switch(p.getStatus()){
		case WHITE:
			//ABBANDONA
			this.patientsAbandoned++;
			p.setStatus(PatientStatus.OUT);
			//lo tolgo dalla waiting room
			waitingRoom.remove(p);
			break;
			
		case YELLOW:
			//diventa rosso ==> cambia lo stato, settare il timeout dei rossi
//NON SI PUO CABIARE LO STATO DELL'OGGETTO DI UNA CODA CHE SI BASA SULLO STATO ALTRIMENTI lei NON SE NE ACCORGE,
//QUINDI LO TOLGO E LO RIMETTO
			waitingRoom.remove(p);
			p.setStatus(PatientStatus.RED);
			
			waitingRoom.add(p);
			
			//aggiungo alla coda l'evento di quando scade il timeout
			queue.add(new Event(p, e.getTime()+RED_TIMEOUT, EventType.TIMEOUT));
			
			break;
			
		case RED:
			//muori analogo al caso bianco
			this.patientsDead++;
			p.setStatus(PatientStatus.BLACK);
			waitingRoom.remove(p);
			break;
			
			
		case OUT:
		case TREATING:
			//timeout arriva troppo tardi alora lo ignoro
			break;
			
		default:
			throw new InternalError("Paziente errato" + p.toString());
		}		
	}

	/**
	 * Patient goes out of triage. A severity code is assigned. If a studio is
	 * free, then it is immediately assigned. Otherwise, he is put in the waiting
	 * list.
	 * 
	 * @param e
	 */
	private void processTriageEvent(Event e) {
		Patient p = e.getPatient();	// finisce il triage quindi gli devo assegnare un codice a caso
		
		int random = (int) (1+Math.random()*3);
		
		if(random ==1)
				p.setStatus(PatientStatus.WHITE);
		if(random ==2)
			p.setStatus(PatientStatus.YELLOW);
		if(random ==3)
			p.setStatus(PatientStatus.RED);
		
//se c'è uno studio libero allora lo mando in cura== 
//impostare tempo di uscita, stato a treating, studi occupati++, nella lista aggiungere che uscirà
		
		if(occupiedStudios<NS){
			int duration = 0;
			
			if(p.getStatus()==PatientStatus.WHITE)
				duration = DURATION_WHITE;
			else if (p.getStatus()==PatientStatus.YELLOW)
				duration = DURATION_YELLOW;
			else if (p.getStatus() == PatientStatus.RED)
				duration = DURATION_RED;
			
			this.occupiedStudios++;
			p.setStatus(PatientStatus.TREATING);
			
			queue.add(new Event (p, e.getTime()+duration, EventType.FREE_STUDIO));
			
		} else {
		//altrimenti va in lista di attesa e schedulo azione di timeout
			int timeout = 0;
			
			if(p.getStatus()==PatientStatus.WHITE)
				timeout = WHITE_TIMEOUT;
			else if (p.getStatus()==PatientStatus.YELLOW)
				timeout = YELLOW_TIMEOUT;
			else if (p.getStatus() == PatientStatus.RED)
				timeout = RED_TIMEOUT;
			
//tempo in cui entra in lista d'attesa va impostato prima di aggiungerlo nella lista, 
//perchè time viene usato nel comparator e nelle code prioritarie la comparazione viene fatta nel 
//momento dell'aggiunta, quindi i campi del confronto dell'oggetto che inserisco devono essere validi, 
//altrimenti se lo cambio dopo la cosa non se ne accorge ed estrae l'ogg nell'ordine errato
			
			p.setQueueTime(e.getTime()); 		
			waitingRoom.add(p);
			
			queue.add(new Event(p, e.getTime()+timeout, EventType.TIMEOUT));
		}
	}

	public int getPatientsTreated() {
		return patientsTreated;
	}

	public int getPatientsDead() {
		return patientsDead;
	}

	public int getPatientsAbandoned() {
		return patientsAbandoned;
	}
}
