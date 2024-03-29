"""added evaluation timestamp

Revision ID: 3b724196e33c
Revises: 0c8407e4fb49
Create Date: 2022-01-28 15:53:46.437497

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '3b724196e33c'
down_revision = '0c8407e4fb49'
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('recording_evaluation', schema=None) as batch_op:
        batch_op.add_column(sa.Column('timestamp', sa.DateTime(), nullable=True))

    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('recording_evaluation', schema=None) as batch_op:
        batch_op.drop_column('timestamp')

    # ### end Alembic commands ###
