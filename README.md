# Incremental improving of gesture Detection based on Obsessive Hand-Washing

Nowadays there are more and more applications which are using machine learning to detect different user processes. Core for a good classification are the training data. But it is often difficult to generate a sufficient amount of data, especially for single developers.  
One approach would be to let users record the training data. In this scenario the process of recording, labeling and transmitting data should be as easy as possible such that everyone can and want do this.  
If the data is recorded and labeled simultaneously as the daily usage of the application, we can gather new data without interaction by the user.

The goal of this project is to extend a machine-learning based classifier via an incremental learning approach to improve the classification.  
New ground truth data should be recorded and transmitted by the same device as used by the classifier. There are different approaches to validate new ground truth data, as using the existing classifier and let the user just confirm this. In this case there is no special interaction needed to record and transfer new data into the *learning algorithm*, for starting learning or to update the classifier.

As a real world example we use the detection of obsessive hand-washing. The classifier is implemented on an Android Smartwatch.  
Furthermore we use a soap dispenser to determine if there've been a new hand-wash action and therefore to trigger the learn event.


## Deliverables
- Project implementation (WearOS app which records/transmits data and communicates with soap dispenser, Server application which receives data, train based on existing model / incremental approach, publishes new classifier)
- Project Documentation (Description of approach, comparison to base model, conclusion)


## Tasks / Roadmap
- [ ] **June:** Related work to incremental machine learning/ efficient sensor listening on WearOS 
- [x] **June:** Concept for overall architecture / Pipeline
- [ ] **June:** WearOS record continuously data in background and efficient way
- [ ] **June:** Observe energy consumption
- [ ] **June:** Evaluate ground truth data (Simple approach via user interaction. Trigger to signal, that recently recorded data is ground truth)
- [ ] **June:** Transmit data to Server
- [ ] **July:** Implement basic learner?
- [ ] **July:** Relearn with extended data
- [ ] **August:** Publish classifier to WearOS
- [ ] **August:** Advanced ground truth data evaluation using existing classifier and less user interaction (confirm)
- [ ] **August:** Communicate with soap dispenser -> no user interaction needed
- [ ] **September:** Incremental learning approach
- [ ] **September:** Refactoring 
- [ ] **October:** Documentation
- [ ] **October:** evaluation



