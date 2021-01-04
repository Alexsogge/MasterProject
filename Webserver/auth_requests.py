class AuthRequest:

    def __init__(self, id, identifier):
        self.id = id
        self.identifier = identifier
        self.granted = False


class AuthRequests:

    def __init__(self):
        self.open_auth_requests = {}


    def get_free_id(self):
        next_id = 0
        while next_id in self.open_auth_requests:
            next_id += 1
        return next_id

    def new_request(self, identifier):
        id = self.get_free_id()
        self.open_auth_requests[id] = AuthRequest(id, identifier)

    def get_by_id(self, id):
        if id not in self.open_auth_requests:
            return None
        else:
            return self.open_auth_requests[id]

    def get_request(self, identifier):
        for key, request in self.open_auth_requests.items():
            if request.identifier == identifier:
                return request
        return None