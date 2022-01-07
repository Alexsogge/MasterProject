from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


class AuthenticationRequest(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    identifier = db.Column(db.String(256), unique=True, nullable=False)
    granted = db.Column(db.Boolean, default=False)
    used = db.Column(db.Boolean, default=False)

    def __repr__(self):
        return f'<AuthRequest[{self.id}]: {self.identifier}>'

    def __str__(self):
        return f'<[{self.id}]: {self.identifier} | {self.granted} | {self.used}>'
