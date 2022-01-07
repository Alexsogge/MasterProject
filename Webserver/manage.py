from app import db, app
import sys

def create_db():
    with app.app_context():
        db.create_all()

if __name__ == '__main__':
    if len(sys.argv) == 1:
        print('usage: manage.py <command>')
    else:
        if sys.argv[1] == 'create_db':
            create_db()